package com.poker.server.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.common.AppConfig;
import com.poker.persistence.JdbcSessionRepository;
import com.poker.persistence.JdbcUserRepository;
import com.poker.persistence.JdbcHandRepository;
import com.poker.persistence.HandRepository;
import com.poker.persistence.HandSettlement;
import com.poker.persistence.UserRepository;
import com.poker.persistence.DatabaseManager;
import com.poker.persistence.JdbcAccountAdministration;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.protocol.ProtocolCodec;
import com.poker.server.lobby.LobbyManager;
import com.poker.server.session.AuthenticationService;
import com.poker.server.session.SessionManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.poker.server.session.UserOperationLocks;

public final class PokerServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PokerServer.class.getName());
    private final AppConfig config;
    private final ProtocolCodec codec = new ProtocolCodec();
    private final ConnectionRegistry connections = new ConnectionRegistry();
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService clientPool = new ThreadPoolExecutor(64, 64, 0, TimeUnit.SECONDS, new SynchronousQueue<>());
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final LobbyManager lobby;
    private final ServerController controller;
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public PokerServer(AppConfig config, DatabaseManager database) {
        this.config = config;
        ObjectMapper mapper = codec.mapper();
        UserRepository users = new JdbcUserRepository(database);
        UserOperationLocks userLocks = new UserOperationLocks();
        var sessions = new SessionManager(new JdbcSessionRepository(database));
        var accounts = new JdbcAccountAdministration(database, mapper);
        var persistedHands = new JdbcHandRepository(database);
        HandRepository hands = new HandRepository() {
            @Override public void createTable(String id, long owner, long blind, int seats) throws java.sql.SQLException {
                persistedHands.createTable(id, owner, blind, seats);
            }
            @Override public boolean settle(HandSettlement settlement) throws java.sql.SQLException {
                boolean committed = persistedHands.settle(settlement);
                if (committed) for (var player : settlement.players()) try {
                    var user = users.findById(player.userId()).orElseThrow();
                    var wallet = mapper.createObjectNode().put("chips", user.chips());
                    wallet.set("glory", mapper.valueToTree(com.poker.model.GlorySummary.from(user.chips(), user.wins(), user.gamesPlayed())));
                    connections.send(user.id(), Message.event(MessageType.WALLET_UPDATED, null, null, 0, wallet));
                } catch (Exception error) {
                    LOG.warning("Wallet notification failed after committed hand; refresh on resume");
                }
                if (committed) try {
                    var dashboard = accounts.dashboard(connections.onlineUserIds());
                    var payload = mapper.valueToTree(dashboard);
                    for (var user : dashboard.users()) if (user.role() == com.poker.model.AccountRole.ADMIN && user.online())
                        connections.send(user.id(), Message.event(MessageType.ADMIN_DASHBOARD, null, null, 0, payload));
                } catch (Exception error) {
                    // Notification failure must never reverse already committed wallet settlement.
                    LOG.warning("Admin dashboard refresh failed after committed hand");
                }
                return committed;
            }
        };
        lobby = new LobbyManager(connections, mapper, config, hands, userLocks);
        controller = new ServerController(new AuthenticationService(users), sessions,
            users, lobby, connections, mapper, accounts, userLocks);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.port(), 50, java.net.InetAddress.getByName(config.host())); running = true;
        heartbeat.scheduleAtFixedRate(this::heartbeat, 10, 10, TimeUnit.SECONDS);
        LOG.info(() -> "Poker server listening on " + config.host() + ":" + serverSocket.getLocalPort());
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler;
                try { handler = new ClientHandler(socket, controller, codec); }
                catch (IOException error) { socket.close(); continue; }
                clients.add(handler);
                try { clientPool.execute(() -> { try { handler.run(); } finally { clients.remove(handler); } }); }
                catch (RejectedExecutionException rejected) { clients.remove(handler); handler.close(); }
            } catch (IOException e) { if (running) throw e; }
        }
    }

    public int localPort() { return serverSocket == null ? -1 : serverSocket.getLocalPort(); }
    private void heartbeat() {
        long cutoff = System.currentTimeMillis() - 30_000;
        for (ClientHandler client : clients) {
            if (client.lastPong() < cutoff) client.close();
            else try { client.send(Message.of(MessageType.PING, codec.mapper().createObjectNode())); } catch (IOException e) { client.close(); }
        }
        // Settlement may remove the last offline seat without another lobby command.
        try { lobby.retireEmptyTables(); }
        catch (RuntimeException error) { LOG.warning("Empty table cleanup failed; retry on next heartbeat"); }
    }

    @Override public void close() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        clients.forEach(ClientHandler::close);
        heartbeat.shutdownNow();
        clientPool.shutdown();
        try {
            if (!clientPool.awaitTermination(2, TimeUnit.SECONDS)) clientPool.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); clientPool.shutdownNow();
        }
        lobby.close();
    }
}
