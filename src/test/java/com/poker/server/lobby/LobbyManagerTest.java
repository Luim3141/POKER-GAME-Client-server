package com.poker.server.lobby;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.common.AppConfig;
import com.poker.model.AccountRole;
import com.poker.model.AccountStatus;
import com.poker.model.Role;
import com.poker.persistence.HandRepository;
import com.poker.persistence.HandSettlement;
import com.poker.persistence.UserAccount;
import com.poker.protocol.ProtocolCodec;
import com.poker.server.network.ClientHandler;
import com.poker.server.network.ConnectionRegistry;
import com.poker.server.session.UserOperationLocks;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyManagerTest {
    @Test void maintenanceRepairsAMissedDisconnectCallbackWithoutRemovingAnOnlineSeat() throws Exception {
        ConnectionRegistry connections = new ConnectionRegistry();
        try (var connection = observerConnection(connections, 1); LobbyManager lobby = lobby(connections)) {
            lobby.create(player(1), 50, 2);
            lobby.retireEmptyTables(); assertTrue(lobby.isSeated(1));
            assertTrue(connections.detach(1, connection.handler()));
            lobby.retireEmptyTables();
            assertFalse(lobby.isSeated(1), "Cleanup must recover even when a disconnect callback was lost");
            assertTrue(lobby.summaries().isEmpty());
        }
    }

    @Test void disconnectedPlayerOutsideHandReleasesSeatAndEmptyRoom() throws Exception {
        try (LobbyManager lobby = lobby(new ConnectionRegistry())) {
            lobby.create(player(1), 50, 2);
            lobby.disconnected(1);
            assertFalse(lobby.isSeated(1), "An offline idle player must not reserve a seat forever");
            assertNull(lobby.currentSnapshot(1));
            assertTrue(lobby.summaries().isEmpty());
        }
    }

    @Test void lastOfflineHandDoesNotLeakItsRoomAfterMaintenance() throws Exception {
        try (LobbyManager lobby = lobby(new ConnectionRegistry(), 1)) {
            String id = lobby.create(player(1), 50, 2).tableId(); lobby.join(player(2), id);
            lobby.ready(1, true); lobby.ready(2, true);
            lobby.disconnected(1); lobby.disconnected(2);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            while (lobby.isSeated(1) && System.nanoTime() < deadline) Thread.sleep(20);
            assertFalse(lobby.isSeated(1)); assertFalse(lobby.isSeated(2));
            lobby.retireEmptyTables();
            assertTrue(lobby.summaries().isEmpty());
            assertNull(lobby.currentSnapshot(1));
        }
    }

    @Test void eightPlayerTableRejectsNinthPlayerButAllowsObserverAndCompletesHand() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new com.poker.server.table.PokerTableRuntime(
            "oversized", 1, 50, 9, 20, (id, message) -> {}, new ObjectMapper()));
        try (LobbyManager lobby = lobby(new ConnectionRegistry())) {
            assertThrows(IllegalArgumentException.class, () -> lobby.create(player(1), 50, 9));
            String tableId = lobby.create(player(1), 50, 8).tableId();
            for (long id = 2; id <= 8; id++) lobby.join(player(id), tableId);
            assertEquals("Table full", assertThrows(IllegalStateException.class,
                () -> lobby.join(player(9), tableId)).getMessage());
            assertFalse(lobby.isSeated(9));
            assertEquals(8, lobby.summaries().getFirst().occupiedSeats());
            var snapshot = lobby.spectate(99, tableId);
            assertEquals(8, snapshot.maxPlayers()); assertEquals(8, snapshot.seats().size());
            assertFalse(lobby.isSeated(99));
            for (long id = 1; id <= 8; id++) lobby.ready(id, true);
            snapshot = lobby.currentSnapshot(99);
            assertEquals(com.poker.model.TableStatus.IN_HAND, snapshot.status());
            assertTrue(snapshot.seats().stream().allMatch(seat -> seat.holeCards().size() == 2));
            assertEquals(com.poker.game.HandPhase.DEALING, snapshot.phase());
            assertNull(snapshot.currentTurnUserId());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (snapshot.phase() == com.poker.game.HandPhase.DEALING && System.nanoTime() < deadline) {
                Thread.sleep(20); snapshot = lobby.currentSnapshot(99);
            }
            for (int action = 0; action < 7; action++) {
                assertNotNull(snapshot.currentTurnUserId());
                lobby.action(snapshot.currentTurnUserId(), com.poker.game.PokerAction.of(
                    com.poker.game.ActionType.FOLD), snapshot.stateVersion());
                snapshot = lobby.currentSnapshot(99);
            }
            assertEquals(com.poker.model.TableStatus.BETWEEN_HANDS, snapshot.status());
            assertEquals(80_000, snapshot.seats().stream().mapToLong(com.poker.server.table.SeatSnapshot::stack).sum());
        }
    }

    @Test void repeatedCreateLeaveRemovesEmptyTablesAndTheirGameThreads() throws Exception {
        var retiredThreadNames = new HashSet<String>();
        try (LobbyManager lobby = lobby(new ConnectionRegistry())) {
            for (int index = 0; index < 25; index++) {
                String tableId = lobby.create(player(1), 50, 2).tableId();
                retiredThreadNames.add("poker-table-" + tableId);
                lobby.leave(1);
                assertFalse(lobby.isSeated(1));
                assertTrue(lobby.summaries().isEmpty());
                assertThrows(IllegalArgumentException.class, () -> lobby.join(player(2), tableId));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (hasRetiredThread(retiredThreadNames) && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(hasRetiredThread(retiredThreadNames), "Retired room threads must terminate");
        }
    }

    @Test void lastLeaveAndJoinCannotCloseAnOccupiedRoomOrLeakAnEmptyOne() throws Exception {
        try (LobbyManager lobby = lobby(new ConnectionRegistry()); var workers = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 20; index++) {
                String tableId = lobby.create(player(1), 50, 2).tableId();
                CyclicBarrier start = new CyclicBarrier(2);
                var leave = workers.submit(() -> { start.await(); lobby.leave(1); return null; });
                var join = workers.submit(() -> {
                    start.await();
                    try { lobby.join(player(2), tableId); return true; }
                    catch (IllegalArgumentException retired) { assertEquals("Table not found", retired.getMessage()); return false; }
                });
                leave.get(3, TimeUnit.SECONDS);
                if (join.get(3, TimeUnit.SECONDS)) {
                    assertTrue(lobby.isSeated(2)); assertEquals(1, lobby.summaries().getFirst().occupiedSeats());
                    lobby.leave(2);
                }
                assertFalse(lobby.isSeated(2)); assertTrue(lobby.summaries().isEmpty());
            }
        }
    }

    @Test void onlineObserverOnlyRoomRetiresAfterLastStop() throws Exception {
        ConnectionRegistry connections = new ConnectionRegistry();
        try (var observer = observerConnection(connections, 3); LobbyManager lobby = lobby(connections)) {
            String tableId = lobby.create(player(1), 50, 2).tableId();
            lobby.spectate(3, tableId); lobby.leave(1);
            assertEquals(1, lobby.summaries().size()); assertEquals(0, lobby.summaries().getFirst().occupiedSeats());
            assertEquals(Role.OBSERVER, lobby.currentSnapshot(3).viewerRole());
            lobby.stopSpectating(3);
            assertTrue(lobby.summaries().isEmpty()); assertNull(lobby.currentSnapshot(3));
        }
    }

    @Test void offlineObserverKeepsOccupiedRoomForResumeButDoesNotRetainEmptyRoom() throws Exception {
        ConnectionRegistry connections = new ConnectionRegistry();
        try (var observer = observerConnection(connections, 3); LobbyManager lobby = lobby(connections)) {
            String tableId = lobby.create(player(1), 50, 2).tableId(); lobby.spectate(3, tableId);
            assertTrue(connections.detach(3, observer.handler())); lobby.disconnected(3);
            assertTrue(lobby.isObserving(3)); assertEquals(tableId, lobby.currentSnapshot(3).tableId());
            assertEquals(Role.OBSERVER, lobby.currentSnapshot(3).viewerRole());
            lobby.leave(1);
            assertFalse(lobby.isObserving(3)); assertNull(lobby.currentSnapshot(3)); assertTrue(lobby.summaries().isEmpty());
        }
    }

    private boolean hasRetiredThread(java.util.Set<String> names) {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> names.contains(thread.getName()) && thread.isAlive());
    }
    private LobbyManager lobby(ConnectionRegistry connections) {
        return lobby(connections, 20);
    }
    private LobbyManager lobby(ConnectionRegistry connections, int turnSeconds) {
        HandRepository inMemoryHands = new HandRepository() {
            @Override public void createTable(String id, long ownerUserId, long smallBlind, int maxPlayers) { }
            @Override public boolean settle(HandSettlement settlement) { return true; }
        };
        return new LobbyManager(connections, new ObjectMapper(),
            new AppConfig("127.0.0.1", 0, "unused", "unused", "", turnSeconds), inMemoryHands, new UserOperationLocks());
    }
    private UserAccount player(long id) {
        return new UserAccount(id, "player_" + id, new byte[32], new byte[16], 1, 10_000, 0, 0, AccountRole.PLAYER, AccountStatus.ACTIVE);
    }
    private ObserverConnection observerConnection(ConnectionRegistry connections, long userId) throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
            ClientHandler handler = new ClientHandler(listener.accept(), null, new ProtocolCodec());
            connections.attach(userId, "observer_" + userId, handler);
            return new ObserverConnection(peer, handler);
        }
    }
    private record ObserverConnection(Socket peer, ClientHandler handler) implements AutoCloseable {
        @Override public void close() throws Exception { handler.close(); peer.close(); }
    }
}
