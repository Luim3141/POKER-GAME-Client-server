package com.poker.server.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poker.game.ActionType;
import com.poker.game.PokerAction;
import com.poker.model.AccountRole;
import com.poker.model.AccountStatus;
import com.poker.model.AdminAction;
import com.poker.persistence.JdbcAccountAdministration;
import com.poker.persistence.UserAccount;
import com.poker.persistence.UserRepository;
import com.poker.protocol.ErrorCode;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.server.lobby.LobbyManager;
import com.poker.server.session.AuthenticationService;
import com.poker.server.session.SessionManager;
import com.poker.server.session.UserOperationLocks;
import java.sql.SQLException;
import java.util.Optional;
import java.util.OptionalLong;

public final class ServerController {
    private final AuthenticationService authentication;
    private final SessionManager sessions;
    private final UserRepository users;
    private final LobbyManager lobby;
    private final ConnectionRegistry connections;
    private final ObjectMapper mapper;
    private final JdbcAccountAdministration accounts;
    private final UserOperationLocks userLocks;

    public ServerController(AuthenticationService authentication, SessionManager sessions, UserRepository users,
                            LobbyManager lobby, ConnectionRegistry connections, ObjectMapper mapper,
                            JdbcAccountAdministration accounts, UserOperationLocks userLocks) {
        this.authentication = authentication; this.sessions = sessions; this.users = users;
        this.lobby = lobby; this.connections = connections; this.mapper = mapper; this.accounts = accounts; this.userLocks = userLocks;
    }

    public void handle(ClientHandler client, Message message) {
        try {
            switch (message.type()) {
                case REGISTER -> authenticate(client, message, true);
                case LOGIN -> authenticate(client, message, false);
                case RESUME_SESSION -> resume(client, message);
                case PING -> client.send(Message.reply(message, MessageType.PONG, mapper.createObjectNode()));
                case PONG -> client.markPong();
                case REVIEW_COIN_REQUEST, ADMIN_ACCOUNT_ACTION -> handleAuthenticated(client, message);
                default -> userLocks.call(client.requireUserId(), () -> {
                    handleAuthenticated(client, message); return null;
                });
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(client, message, errorCode(e.getMessage()), e.getMessage());
        } catch (SQLException e) {
            sendError(client, message, ErrorCode.SERVER_ERROR, "Database operation failed");
        } catch (Exception e) {
            sendError(client, message, ErrorCode.SERVER_ERROR, "Server could not process the request");
        }
    }

    private void authenticate(ClientHandler client, Message request, boolean register) throws Exception {
        requireUnauthenticated(client);
        String username = requiredText(request.payload(), "username");
        char[] password = requiredPassword(request.payload(), "password");
        try {
            UserAccount candidate = register ? authentication.register(username, password)
                : authentication.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Login failed"));
            userLocks.call(candidate.id(), () -> {
                UserAccount user = register ? authentication.find(candidate.id()).orElseThrow()
                    : authentication.login(username, password).orElseThrow(() -> new IllegalArgumentException("Login failed"));
                if (user.accountStatus() != AccountStatus.ACTIVE) throw new IllegalStateException("Account locked");
                completeAuthentication(client, request, user, sessions.create(user.id()));
                return null;
            });
        }
        finally { java.util.Arrays.fill(password, '\0'); }
        pushAdminDashboard();
    }

    private void completeAuthentication(ClientHandler client, Message request, UserAccount user, String token) throws Exception {
        attach(client, user);
        ObjectNode payload = mapper.createObjectNode().put("sessionId", token).put("userId", user.id())
            .put("username", user.username()).put("chips", user.chips())
            .put("accountRole", user.accountRole().name()).put("accountStatus", user.accountStatus().name());
        payload.set("glory", mapper.valueToTree(com.poker.model.GlorySummary.from(user.chips(), user.wins(), user.gamesPlayed())));
        client.send(Message.reply(request, MessageType.LOGIN_SUCCESS, payload));
        lobby.reconnected(user.id());
        sendSnapshotIfPresent(client, user.id());
    }

    private void resume(ClientHandler client, Message request) throws Exception {
        requireUnauthenticated(client);
        String token = requiredText(request.payload(), "sessionId");
        OptionalLong userId = sessions.resume(token);
        if (userId.isEmpty()) throw new IllegalArgumentException("Session expired");
        userLocks.call(userId.getAsLong(), () -> {
            if (sessions.resume(token).orElse(-1) != userId.getAsLong()) throw new IllegalArgumentException("Session expired");
            UserAccount user = authentication.find(userId.getAsLong()).orElseThrow(() -> new IllegalArgumentException("Session expired"));
            if (user.accountStatus() != AccountStatus.ACTIVE) throw new IllegalStateException("Account locked");
            completeAuthentication(client, request, user, token);
            return null;
        });
        pushAdminDashboard();
    }

    private void attach(ClientHandler client, UserAccount user) {
        client.authenticate(user.id(), user.username()); connections.attach(user.id(), user.username(), client);
    }

    private void handleAuthenticated(ClientHandler client, Message request) throws Exception {
        long userId = client.requireUserId();
        if (!connections.isCurrent(userId, client)) throw new IllegalStateException("Session connection was replaced");
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found"));
        if (user.accountStatus() != AccountStatus.ACTIVE) throw new IllegalStateException("Account locked");
        authorize(user.accountRole(), request.type());
        switch (request.type()) {
            case LOGOUT -> userLocks.call(userId, () -> {
                String token = requiredText(request.payload(), "sessionId");
                if (sessions.resume(token).orElse(-1) != userId) throw new IllegalArgumentException("Session expired");
                sessions.revoke(token); lobby.forceLeave(userId); client.close(); return null;
            });
            case GET_LOBBY -> sendLobby(client, request);
            case GET_HISTORY -> client.send(Message.reply(request, MessageType.HISTORY_SNAPSHOT, historyPayload(userId)));
            case CREATE_TABLE -> userLocks.call(userId, () -> {
                UserAccount current = authorizedUser(userId, request.type());
                client.send(Message.reply(request, MessageType.TABLE_STATE, mapper.valueToTree(
                    lobby.create(current, requiredLong(request.payload(), "smallBlind"), requiredInt(request.payload(), "maxPlayers")))));
                return null;
            });
            case JOIN_TABLE -> userLocks.call(userId, () -> {
                UserAccount current = authorizedUser(userId, request.type());
                client.send(Message.reply(request, MessageType.TABLE_STATE, mapper.valueToTree(
                    lobby.join(current, requiredText(request.payload(), "tableId")))));
                return null;
            });
            case LEAVE_TABLE, LEAVE_SEAT -> { lobby.leave(userId); sendLobby(client, request); }
            case READY -> lobby.ready(userId, true);
            case UNREADY -> lobby.ready(userId, false);
            case SPECTATE_TABLE -> client.send(Message.reply(request, MessageType.TABLE_STATE, mapper.valueToTree(
                lobby.spectate(userId, requiredText(request.payload(), "tableId")))));
            case STOP_SPECTATE -> { lobby.stopSpectating(userId); sendLobby(client, request); }
            case PLAYER_ACTION -> {
                var snapshot = lobby.currentSnapshot(userId);
                if (snapshot == null) throw new IllegalStateException("Not seated");
                if (!snapshot.tableId().equals(request.tableId()) || !java.util.Objects.equals(snapshot.handId(), request.handId()))
                    throw new IllegalStateException("Stale table or hand state");
                lobby.action(userId, new PokerAction(ActionType.valueOf(requiredText(request.payload(), "action")),
                    request.payload().has("amount") ? requiredLong(request.payload(), "amount") : 0), request.seq());
                client.send(Message.reply(request, MessageType.ACTION_ACCEPTED, mapper.createObjectNode()));
            }
            case INVITE_PLAYER -> invite(client, request);
            case INVITE_RESPONSE -> {
                if (request.payload().path("accepted").asBoolean(false))
                    userLocks.call(userId, () -> {
                        UserAccount current = authorizedUser(userId, request.type());
                        client.send(Message.reply(request, MessageType.TABLE_STATE, mapper.valueToTree(lobby.join(current, requiredText(request.payload(), "tableId")))));
                        return null;
                    });
            }
            case SUBMIT_COIN_REQUEST -> {
                accounts.submitCoinRequest(userId, requiredLong(request.payload(), "amount"));
                sendCoinRequests(client, userId); pushAdminDashboard();
            }
            case GET_COIN_REQUESTS -> sendCoinRequests(client, userId);
            case GET_ADMIN_DASHBOARD -> sendAdminDashboard(client);
            case REVIEW_COIN_REQUEST -> reviewCoinRequest(client, userId, request);
            case ADMIN_ACCOUNT_ACTION -> adminAccountAction(client, userId, request);
            default -> throw new IllegalArgumentException("Unsupported message: " + request.type());
        }
    }

    private UserAccount authorizedUser(long userId, MessageType type) throws SQLException {
        UserAccount user = users.findById(userId).orElseThrow(() -> new IllegalStateException("User not found"));
        if (user.accountStatus() != AccountStatus.ACTIVE) throw new IllegalStateException("Account locked");
        authorize(user.accountRole(), type);
        return user;
    }

    private void reviewCoinRequest(ClientHandler client, long adminId, Message request) throws Exception {
        long requestId = requiredLong(request.payload(), "requestId");
        long targetId = accounts.requestOwner(requestId);
        userLocks.call(targetId, () -> {
            if (request.payload().path("approved").asBoolean(false) && lobby.isSeated(targetId))
                throw new IllegalStateException("Player is still seated at a table");
            accounts.review(adminId, requestId, request.payload().path("approved").asBoolean(false), request.payload().path("note").asText(""));
            return null;
        });
        sendCoinRequestsTo(targetId); sendWallet(targetId); pushAdminDashboard(); sendAdminDashboard(client);
    }

    private void adminAccountAction(ClientHandler client, long adminId, Message request) throws Exception {
        long targetId = requiredLong(request.payload(), "userId");
        AdminAction action = AdminAction.valueOf(requiredText(request.payload(), "action"));
        userLocks.call(targetId, () -> {
            boolean busy = connections.isOnline(targetId) || lobby.isSeated(targetId);
            if (action != AdminAction.LOCK && action != AdminAction.UNLOCK && busy)
                throw new IllegalStateException("User must be offline and outside every table");
            if (action == AdminAction.SET_ROLE && lobby.isObserving(targetId))
                throw new IllegalStateException("User must stop observing before changing role");
            switch (action) {
                case LOCK -> {
                    lobby.forceLeave(targetId); accounts.setStatus(adminId, targetId, AccountStatus.LOCKED);
                    sessions.revokeAll(targetId);
                    connections.sendAndClose(targetId, Message.event(MessageType.ACCOUNT_LOCKED, null, null, 0,
                        mapper.createObjectNode().put("message", "Tài khoản đã bị khóa bởi Admin")));
                }
                case UNLOCK -> accounts.setStatus(adminId, targetId, AccountStatus.ACTIVE);
                case SET_ROLE -> accounts.setRole(adminId, targetId, AccountRole.valueOf(requiredText(request.payload(), "role")));
                case RESET_PASSWORD -> {
                    char[] password = requiredPassword(request.payload(), "temporaryPassword");
                    try { accounts.resetPassword(adminId, targetId, password); sessions.revokeAll(targetId); }
                    finally { java.util.Arrays.fill(password, '\0'); }
                }
                case ADJUST_COINS -> accounts.adjustCoins(adminId, targetId, requiredLong(request.payload(), "delta"), requiredText(request.payload(), "reason"));
            }
            return null;
        });
        pushAdminDashboard(); sendAdminDashboard(client);
    }

    private void authorize(AccountRole role, MessageType type) {
        if (type == MessageType.LOGOUT) return;
        if (role == AccountRole.OBSERVER && type == MessageType.PLAYER_ACTION)
            throw new IllegalStateException("Observer cannot act");
        boolean allowed = switch (role) {
            case ADMIN -> java.util.Set.of(MessageType.GET_ADMIN_DASHBOARD, MessageType.REVIEW_COIN_REQUEST, MessageType.ADMIN_ACCOUNT_ACTION).contains(type);
            case OBSERVER -> java.util.Set.of(MessageType.GET_LOBBY, MessageType.SPECTATE_TABLE, MessageType.STOP_SPECTATE).contains(type);
            case PLAYER -> java.util.Set.of(MessageType.GET_LOBBY, MessageType.GET_HISTORY, MessageType.CREATE_TABLE,
                MessageType.JOIN_TABLE, MessageType.LEAVE_TABLE, MessageType.LEAVE_SEAT, MessageType.READY,
                MessageType.UNREADY, MessageType.PLAYER_ACTION, MessageType.INVITE_PLAYER, MessageType.INVITE_RESPONSE,
                MessageType.SUBMIT_COIN_REQUEST, MessageType.GET_COIN_REQUESTS).contains(type);
        };
        if (!allowed) throw new IllegalStateException("Unauthorized for account role " + role);
    }

    private void sendCoinRequests(ClientHandler client, long userId) throws Exception {
        client.send(Message.event(MessageType.COIN_REQUEST_SNAPSHOT, null, null, 0, mapper.valueToTree(accounts.requestsFor(userId))));
    }
    private void sendCoinRequestsTo(long userId) throws Exception {
        connections.send(userId, Message.event(MessageType.COIN_REQUEST_SNAPSHOT, null, null, 0, mapper.valueToTree(accounts.requestsFor(userId))));
    }
    private void sendWallet(long userId) throws Exception {
        UserAccount user = users.findById(userId).orElseThrow();
        ObjectNode payload = mapper.createObjectNode().put("chips", user.chips());
        payload.set("glory", mapper.valueToTree(com.poker.model.GlorySummary.from(user.chips(), user.wins(), user.gamesPlayed())));
        connections.send(userId, Message.event(MessageType.WALLET_UPDATED, null, null, 0, payload));
    }
    private void sendAdminDashboard(ClientHandler client) throws Exception {
        client.send(Message.event(MessageType.ADMIN_DASHBOARD, null, null, 0, mapper.valueToTree(accounts.dashboard(connections.onlineUserIds()))));
    }
    private void pushAdminDashboard() throws Exception {
        var current = accounts.dashboard(connections.onlineUserIds());
        var dashboard = mapper.valueToTree(current);
        for (var admin : current.users()) if (admin.role() == AccountRole.ADMIN && admin.online())
            connections.send(admin.id(), Message.event(MessageType.ADMIN_DASHBOARD, null, null, 0, dashboard));
    }

    private void invite(ClientHandler client, Message request) throws Exception {
        String username = requiredText(request.payload(), "username");
        UserAccount invited = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        var snapshot = lobby.currentSnapshot(client.requireUserId());
        String tableId = requiredText(request.payload(), "tableId");
        if (snapshot == null || snapshot.viewerRole() != com.poker.model.Role.PLAYER || !snapshot.tableId().equals(tableId))
            throw new IllegalStateException("Not seated at the invited table");
        if (invited.accountRole() != AccountRole.PLAYER || invited.accountStatus() != AccountStatus.ACTIVE)
            throw new IllegalStateException("Only active players can be invited");
        if (!connections.isOnline(invited.id())) throw new IllegalStateException("User is offline");
        connections.send(invited.id(), Message.event(MessageType.INVITE_RECEIVED, tableId, null, 0,
            mapper.createObjectNode().put("from", client.username()).put("tableId", tableId)));
    }

    private void sendLobby(ClientHandler client, Message request) throws Exception {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("seatReleasePending", lobby.isSeated(client.requireUserId()));
        payload.set("onlineUsers", mapper.valueToTree(connections.onlineUsers()));
        payload.set("tables", mapper.valueToTree(lobby.summaries()));
        payload.set("ranking", mapper.valueToTree(users.ranking(100)));
        client.send(Message.reply(request, MessageType.LOBBY_SNAPSHOT, payload));
    }

    private void sendSnapshotIfPresent(ClientHandler client, long userId) throws Exception {
        var snapshot = lobby.currentSnapshot(userId);
        if (snapshot != null) client.send(Message.event(MessageType.STATE_SNAPSHOT, snapshot.tableId(), null, snapshot.stateVersion(), mapper.valueToTree(snapshot)));
    }

    private JsonNode historyPayload(long userId) throws SQLException {
        var payload = mapper.createArrayNode();
        for (var entry : users.history(userId, 100)) {
            ObjectNode item = payload.addObject();
            item.put("handId", entry.handId()).put("tableId", entry.tableId()).put("result", entry.result())
                .put("contribution", entry.contribution()).put("startingStack", entry.startingStack())
                .put("finalStack", entry.finalStack()).put("startedAt", entry.startedAt().toString());
            if (entry.endedAt() == null) item.putNull("endedAt"); else item.put("endedAt", entry.endedAt().toString());
        }
        return payload;
    }

    private void requireUnauthenticated(ClientHandler client) {
        if (client.userId() != null) throw new IllegalStateException("Already authenticated");
    }

    public void disconnected(ClientHandler client) {
        Long id = client.userId();
        if (id == null) return;
        try {
            boolean detached = userLocks.call(id, () -> {
                if (!connections.detach(id, client)) return false;
                lobby.disconnected(id); return true;
            });
            if (detached) pushAdminDashboard();
        } catch (Exception ignored) { /* connection is already closed */ }
    }

    private void sendError(ClientHandler client, Message request, ErrorCode code, String detail) {
        try { client.send(Message.reply(request, request.type() == MessageType.PLAYER_ACTION ? MessageType.ACTION_REJECTED : MessageType.ERROR,
            mapper.createObjectNode().put("code", code.name()).put("message", detail).put("requestType", request.type().name()))); }
        catch (Exception ignored) { client.close(); }
    }

    private ErrorCode errorCode(String message) {
        if (message == null) return ErrorCode.INVALID_REQUEST;
        if (message.contains("Authentication")) return ErrorCode.AUTH_REQUIRED;
        if (message.contains("Dealing")) return ErrorCode.NOT_READY;
        if (message.contains("turn")) return ErrorCode.NOT_YOUR_TURN;
        if (message.contains("Stale")) return ErrorCode.STALE_STATE;
        if (message.contains("Table full")) return ErrorCode.TABLE_FULL;
        if (message.contains("Already seated")) return ErrorCode.ALREADY_SEATED;
        if (message.contains("Not seated")) return ErrorCode.NOT_SEATED;
        if (message.contains("Observer")) return ErrorCode.OBSERVER_CANNOT_ACT;
        if (message.contains("still seated")) return ErrorCode.PLAYER_AT_TABLE;
        if (message.contains("locked")) return ErrorCode.ACCOUNT_LOCKED;
        if (message.contains("already reviewed")) return ErrorCode.REQUEST_ALREADY_REVIEWED;
        if (message.contains("Unauthorized") || message.contains("Admin authorization")) return ErrorCode.UNAUTHORIZED;
        if (message.contains("Session")) return ErrorCode.SESSION_EXPIRED;
        if (message.contains("Login")) return ErrorCode.LOGIN_FAILED;
        return ErrorCode.INVALID_REQUEST;
    }

    private String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText("").trim(); if (value.isEmpty()) throw new IllegalArgumentException("Missing " + name); return value;
    }
    private char[] requiredPassword(JsonNode node, String name) {
        if (!node.path(name).isTextual()) throw new IllegalArgumentException("Missing " + name);
        return node.path(name).asText().toCharArray();
    }
    private long requiredLong(JsonNode node, String name) { if (!node.path(name).isIntegralNumber() || !node.path(name).canConvertToLong()) throw new IllegalArgumentException("Invalid " + name); return node.path(name).asLong(); }
    private int requiredInt(JsonNode node, String name) { long value = requiredLong(node, name); if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid " + name); return (int) value; }
}
