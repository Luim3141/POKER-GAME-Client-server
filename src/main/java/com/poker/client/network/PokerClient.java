package com.poker.client.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.poker.client.state.ClientState;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.protocol.ProtocolCodec;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PokerClient implements AutoCloseable {
    private final ProtocolCodec codec = new ProtocolCodec();
    private final ClientState state = new ClientState();
    private final ClientEventListener listener;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ServerConnection connection;
    private Thread receiver;

    public PokerClient(ClientEventListener listener) { this.listener = listener; }

    public synchronized void connect(String host, int port) throws IOException {
        if (running.get()) throw new IllegalStateException("Already connected");
        ServerConnection next = new ServerConnection(codec);
        try { next.connect(host, port); }
        catch (IOException | RuntimeException error) { next.close(); throw error; }
        connection = next; running.set(true);
        receiver = Thread.ofPlatform().name("poker-client-receiver").start(() -> receiveLoop(next));
    }

    public void register(String username, String password) throws IOException { credentials(MessageType.REGISTER, username, password); }
    public void login(String username, String password) throws IOException { credentials(MessageType.LOGIN, username, password); }
    public void resume(String sessionId) throws IOException {
        send(MessageType.RESUME_SESSION, codec.mapper().createObjectNode().put("sessionId", sessionId), null, 0);
    }
    public void requestLobby() throws IOException { send(MessageType.GET_LOBBY, empty(), null, 0); }
    public void requestHistory() throws IOException { send(MessageType.GET_HISTORY, empty(), null, 0); }
    public void requestCoinRequests() throws IOException { send(MessageType.GET_COIN_REQUESTS, empty(), null, 0); }
    public void submitCoinRequest(long amount) throws IOException {
        send(MessageType.SUBMIT_COIN_REQUEST, codec.mapper().createObjectNode().put("amount", amount), null, 0);
    }
    public void requestAdminDashboard() throws IOException { send(MessageType.GET_ADMIN_DASHBOARD, empty(), null, 0); }
    public void reviewCoinRequest(long requestId, boolean approved, String note) throws IOException {
        send(MessageType.REVIEW_COIN_REQUEST, codec.mapper().createObjectNode().put("requestId", requestId)
            .put("approved", approved).put("note", note == null ? "" : note), null, 0);
    }
    public void adminAccountAction(long userId, String action, String role, String temporaryPassword, long delta, String reason) throws IOException {
        ObjectNode payload = codec.mapper().createObjectNode().put("userId", userId).put("action", action);
        if (role != null) payload.put("role", role);
        if (temporaryPassword != null) payload.put("temporaryPassword", temporaryPassword);
        if (delta != 0) payload.put("delta", delta);
        if (reason != null) payload.put("reason", reason);
        send(MessageType.ADMIN_ACCOUNT_ACTION, payload, null, 0);
    }
    public void logout() throws IOException {
        if (!state.authenticated()) return;
        send(MessageType.LOGOUT, codec.mapper().createObjectNode().put("sessionId", state.sessionId()), null, 0);
    }
    public void createTable(long smallBlind, int maxPlayers) throws IOException {
        send(MessageType.CREATE_TABLE, codec.mapper().createObjectNode().put("smallBlind", smallBlind).put("maxPlayers", maxPlayers), null, 0);
    }
    public void joinTable(String id) throws IOException { send(MessageType.JOIN_TABLE, codec.mapper().createObjectNode().put("tableId", id), id, 0); }
    public void spectate(String id) throws IOException { send(MessageType.SPECTATE_TABLE, codec.mapper().createObjectNode().put("tableId", id), id, 0); }
    public void ready(boolean ready) throws IOException { send(ready ? MessageType.READY : MessageType.UNREADY, empty(), tableId(), state.table() == null ? 0 : state.table().stateVersion()); }
    public void action(String action, long amount) throws IOException {
        action(action, amount, state.table());
    }
    public void action(String action, long amount, com.poker.server.table.TableSnapshot table) throws IOException {
        var current = state.table();
        if (table == null || current == null) throw new IllegalStateException("Not at a table");
        if (!table.tableId().equals(current.tableId()) || table.stateVersion() != current.stateVersion() ||
            !java.util.Objects.equals(table.handId(), current.handId())) throw new IllegalStateException("Table changed before action was sent");
        ObjectNode payload = codec.mapper().createObjectNode().put("action", action).put("amount", amount);
        connection.send(new Message(1, MessageType.PLAYER_ACTION, java.util.UUID.randomUUID().toString(),
            state.sessionId(), table.tableId(), table.handId(), table.stateVersion(), System.currentTimeMillis(), payload));
    }
    public void leaveTable() throws IOException { send(MessageType.LEAVE_TABLE, empty(), tableId(), 0); }
    public void stopSpectating() throws IOException { send(MessageType.STOP_SPECTATE, empty(), tableId(), 0); }
    public void invite(String username) throws IOException {
        send(MessageType.INVITE_PLAYER, codec.mapper().createObjectNode().put("username", username).put("tableId", tableId()), tableId(), 0);
    }
    public void answerInvitation(String tableId, boolean accepted) throws IOException {
        send(MessageType.INVITE_RESPONSE, codec.mapper().createObjectNode().put("tableId", tableId).put("accepted", accepted), tableId, 0);
    }

    private void credentials(MessageType type, String username, String password) throws IOException {
        send(type, codec.mapper().createObjectNode().put("username", username).put("password", password), null, 0);
    }
    private void send(MessageType type, JsonNode payload, String tableId, long seq) throws IOException {
        if (connection == null || !connection.isConnected()) throw new IllegalStateException("Not connected");
        connection.send(Message.request(type, state.sessionId(), tableId, seq, payload));
    }
    private ObjectNode empty() { return codec.mapper().createObjectNode(); }
    private String tableId() { return state.table() == null ? null : state.table().tableId(); }

    private void receiveLoop(ServerConnection active) {
        Throwable failure = null;
        try {
            while (running.get() && connection == active) {
                Message message = active.receive();
                if (message.type() == MessageType.PING) {
                    active.send(Message.reply(message, MessageType.PONG, empty()));
                    continue;
                }
                synchronized (this) {
                    if (connection != active || !running.get()) break;
                    state.apply(message, codec.mapper());
                }
                listener.onMessage(message);
            }
        } catch (EOFException ignored) { }
        catch (Throwable error) { failure = error; }
        finally {
            boolean notify;
            synchronized (this) {
                try { active.close(); } catch (IOException ignored) { }
                notify = connection == active;
                if (notify) running.set(false);
            }
            if (notify && connection == active) listener.onDisconnected(failure);
        }
    }

    public ClientState state() { return state; }
    public boolean isConnected() { return running.get(); }
    @Override public synchronized void close() throws IOException { running.set(false); if (connection != null) connection.close(); }
}
