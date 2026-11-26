package com.poker.server.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.common.AppConfig;
import com.poker.model.AccountRole;
import com.poker.model.AccountStatus;
import com.poker.persistence.*;
import com.poker.protocol.*;
import com.poker.server.lobby.LobbyManager;
import com.poker.server.session.UserOperationLocks;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerControllerTest {
    @Test void replacedConnectionCannotReadyOrLeaveTheNewConnectionsSeat() throws Exception {
        var mapper = new ObjectMapper();
        var registry = new ConnectionRegistry();
        var locks = new UserOperationLocks();
        var user = new UserAccount(1, "player", new byte[32], new byte[16], 1, 10000, 0, 0,
            AccountRole.PLAYER, AccountStatus.ACTIVE);
        UserRepository users = new UserRepository() {
            public UserAccount create(String name, byte[] hash, byte[] salt, int iterations, long chips) { return user; }
            public Optional<UserAccount> findByUsername(String name) { return Optional.of(user); }
            public Optional<UserAccount> findById(long id) { return Optional.of(user); }
            public List<RankingEntry> ranking(int limit) { return List.of(); }
            public List<HistoryEntry> history(long id, int limit) { return List.of(); }
        };
        HandRepository hands = new HandRepository() {
            public void createTable(String id, long owner, long blind, int seats) { }
            public boolean settle(HandSettlement hand) { return true; }
        };
        try (LobbyManager lobby = new LobbyManager(registry, mapper,
                 new AppConfig("127.0.0.1", 0, "unused", "unused", "", 20), hands, locks);
             ServerSocket listener = new ServerSocket(0);
             Socket oldPeer = new Socket("127.0.0.1", listener.getLocalPort());
             ClientHandler old = new ClientHandler(listener.accept(), null, new ProtocolCodec());
             Socket newPeer = new Socket("127.0.0.1", listener.getLocalPort());
             ClientHandler current = new ClientHandler(listener.accept(), null, new ProtocolCodec())) {
            old.authenticate(1, user.username()); registry.attach(1, user.username(), old);
            lobby.create(user, 50, 2);
            current.authenticate(1, user.username()); registry.attach(1, user.username(), current);
            var controller = new ServerController(null, null, users, lobby, registry, mapper, null, locks);
            controller.handle(old, Message.of(MessageType.READY, mapper.createObjectNode()));
            assertFalse(lobby.currentSnapshot(1).seats().getFirst().ready(), "An old socket must not mutate a resumed seat");
            controller.handle(old, Message.of(MessageType.LEAVE_TABLE, mapper.createObjectNode()));
            assertTrue(lobby.isSeated(1));
            controller.disconnected(old);
            assertTrue(lobby.currentSnapshot(1).seats().getFirst().connected(), "Old disconnect must not detach the new socket");
        }
    }
}
