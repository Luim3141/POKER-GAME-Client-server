package com.poker.server.network;

import com.poker.client.network.ClientEventListener;
import com.poker.client.network.PokerClient;
import com.poker.common.AppConfig;
import com.poker.persistence.DatabaseManager;
import com.poker.persistence.JdbcAccountAdministration;
import com.poker.persistence.JdbcUserRepository;
import com.poker.common.PasswordHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ServerEndToEndIT {
    @Test void offlineWinnerIsCreditedAndResumeAfterSettlementDoesNotRestoreAGhostSeat() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 20);
        DatabaseManager database = new DatabaseManager(config);
        JdbcUserRepository users = new JdbcUserRepository(database);
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        String firstName = username(), secondName = username(), tableId = null, handId = null;
        TestListener firstEvents = new TestListener(), secondEvents = new TestListener();
        try (PokerClient first = new PokerClient(firstEvents); PokerClient second = new PokerClient(secondEvents)) {
            first.connect("127.0.0.1", server.localPort()); first.register(firstName, "offline-pass");
            firstEvents.await(MessageType.LOGIN_SUCCESS);
            second.connect("127.0.0.1", server.localPort()); second.register(secondName, "offline-pass");
            Message login = secondEvents.await(MessageType.LOGIN_SUCCESS);
            long secondId = login.payload().path("userId").asLong(); String token = login.payload().path("sessionId").asText();
            first.createTable(50, 2); tableId = firstEvents.await(MessageType.TABLE_STATE).payload().path("tableId").asText();
            second.joinTable(tableId); secondEvents.await(MessageType.TABLE_STATE);
            first.ready(true); second.ready(true); awaitActiveTable(firstEvents); awaitActiveTable(secondEvents);
            second.close();
            Message disconnected;
            do { disconnected = firstEvents.await(MessageType.TABLE_STATE); }
            while (java.util.stream.StreamSupport.stream(disconnected.payload().path("seats").spliterator(), false)
                .noneMatch(seat -> seat.path("userId").asLong() == secondId && !seat.path("connected").asBoolean()));
            first.action("FOLD", 0);
            Message result = firstEvents.await(MessageType.HAND_RESULT); handId = result.handId();
            assertEquals(secondId, result.payload().path("winners").get(0).path("userId").asLong());
            awaitTableStatus(firstEvents, "BETWEEN_HANDS");
            assertEquals(1, first.state().table().seats().size());
            assertEquals(10050, users.findById(secondId).orElseThrow().chips());
            assertEquals(20000, users.findByUsername(firstName).orElseThrow().chips() + users.findById(secondId).orElseThrow().chips());
            TestListener returningEvents = new TestListener();
            try (PokerClient returning = new PokerClient(returningEvents)) {
                returning.connect("127.0.0.1", server.localPort()); returning.resume(token); returningEvents.await(MessageType.LOGIN_SUCCESS);
                returning.requestLobby();
                assertFalse(returningEvents.await(MessageType.LOBBY_SNAPSHOT).payload().path("seatReleasePending").asBoolean());
                assertNull(returning.state().table());
                assertEquals(10050, returning.state().chips());
            }
        } finally {
            server.close(); serverThread.join(5000);
            if (handId != null) try (var connection = database.open()) {
                for (String sql : java.util.List.of("DELETE FROM wallet_transactions WHERE hand_id=?", "DELETE FROM hand_actions WHERE hand_id=?",
                    "DELETE FROM hand_players WHERE hand_id=?", "DELETE FROM hands WHERE id=?"))
                    try (var statement = connection.prepareStatement(sql)) { statement.setString(1, handId); statement.executeUpdate(); }
            }
            if (tableId != null) try (var connection = database.open(); var statement = connection.prepareStatement("DELETE FROM poker_tables WHERE id=?")) {
                statement.setString(1, tableId); statement.executeUpdate();
            }
            cleanupUsers(database, firstName, secondName);
        }
    }

    @Test void offlineObserverCannotChangeRoleWhileStillSubscribedToAnOccupiedTable() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 20);
        DatabaseManager database = new DatabaseManager(config);
        JdbcUserRepository users = new JdbcUserRepository(database);
        String ownerName = username(), observerName = username(), adminName = username();
        var hash = new PasswordHasher().hash("observer-role-pass".toCharArray());
        users.create(ownerName, hash.hash(), hash.salt(), hash.iterations(), 10000);
        long observerId = users.create(observerName, hash.hash(), hash.salt(), hash.iterations(), 0).id();
        try (var connection = database.open(); var statement = connection.prepareStatement("UPDATE users SET account_role='OBSERVER' WHERE id=?")) {
            statement.setLong(1, observerId); statement.executeUpdate();
        }
        new JdbcAccountAdministration(database, new ObjectMapper()).createAdmin(adminName, hash.hash(), hash.salt(), hash.iterations());
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        TestListener ownerEvents = new TestListener(), observerEvents = new TestListener(), adminEvents = new TestListener();
        String tableId = null;
        try (PokerClient owner = new PokerClient(ownerEvents); PokerClient observer = new PokerClient(observerEvents); PokerClient admin = new PokerClient(adminEvents)) {
            owner.connect("127.0.0.1", server.localPort()); owner.login(ownerName, "observer-role-pass"); ownerEvents.await(MessageType.LOGIN_SUCCESS);
            observer.connect("127.0.0.1", server.localPort()); observer.login(observerName, "observer-role-pass");
            String token = observerEvents.await(MessageType.LOGIN_SUCCESS).payload().path("sessionId").asText();
            admin.connect("127.0.0.1", server.localPort()); admin.login(adminName, "observer-role-pass"); adminEvents.await(MessageType.LOGIN_SUCCESS);
            owner.createTable(50, 2); tableId = ownerEvents.await(MessageType.TABLE_STATE).payload().path("tableId").asText();
            observer.spectate(tableId); observerEvents.await(MessageType.TABLE_STATE); observer.close();
            boolean offline = false;
            while (!offline) {
                Message dashboard = adminEvents.await(MessageType.ADMIN_DASHBOARD);
                for (var user : dashboard.payload().path("users")) if (user.path("id").asLong() == observerId && !user.path("online").asBoolean()) offline = true;
            }
            admin.adminAccountAction(observerId, "SET_ROLE", "PLAYER", null, 0, null);
            assertEquals(MessageType.ERROR, adminEvents.await(MessageType.ERROR).type());
            assertEquals(com.poker.model.AccountRole.OBSERVER, users.findById(observerId).orElseThrow().accountRole());
            TestListener resumedEvents = new TestListener();
            try (PokerClient resumed = new PokerClient(resumedEvents)) {
                resumed.connect("127.0.0.1", server.localPort()); resumed.resume(token); resumedEvents.await(MessageType.LOGIN_SUCCESS);
                Message snapshot = resumedEvents.await(MessageType.STATE_SNAPSHOT);
                assertEquals(tableId, snapshot.tableId()); assertEquals("OBSERVER", snapshot.payload().path("viewerRole").asText());
            }
        } finally {
            server.close(); serverThread.join(5000);
            if (tableId != null) try (var connection = database.open(); var statement = connection.prepareStatement("DELETE FROM poker_tables WHERE id=?")) {
                statement.setString(1, tableId); statement.executeUpdate();
            }
            cleanupUsers(database, ownerName, observerName, adminName);
        }
    }
    @Test void threePlayersAndObserverPlayAllInSidePotsWithPersistedConservation() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 20);
        DatabaseManager database = new DatabaseManager(config);
        JdbcUserRepository users = new JdbcUserRepository(database);
        String[] names = {username(), username(), username(), username()};
        long[] startingWallets = {10_000, 3_000, 7_000};
        long[] ids = new long[4];
        var password = new PasswordHasher().hash("sidepot-password".toCharArray());
        for (int index = 0; index < names.length; index++) ids[index] = users.create(names[index], password.hash(), password.salt(),
            password.iterations(), index < 3 ? startingWallets[index] : 0).id();
        try (var connection = database.open(); var statement = connection.prepareStatement("UPDATE users SET account_role='OBSERVER' WHERE id=?")) {
            statement.setLong(1, ids[3]); statement.executeUpdate();
        }
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        TestListener[] events = {new TestListener(), new TestListener(), new TestListener(), new TestListener()};
        String tableId = null;
        try (PokerClient highest = new PokerClient(events[0]); PokerClient shortest = new PokerClient(events[1]);
             PokerClient middle = new PokerClient(events[2]); PokerClient observer = new PokerClient(events[3])) {
            PokerClient[] clients = {highest, shortest, middle, observer};
            for (int index = 0; index < clients.length; index++) {
                clients[index].connect("127.0.0.1", server.localPort()); clients[index].login(names[index], "sidepot-password");
                events[index].await(MessageType.LOGIN_SUCCESS);
            }
            highest.createTable(50, 3); events[0].await(MessageType.TABLE_STATE); tableId = highest.state().table().tableId();
            shortest.joinTable(tableId); events[1].await(MessageType.TABLE_STATE);
            middle.joinTable(tableId); events[2].await(MessageType.TABLE_STATE);
            observer.spectate(tableId); events[3].await(MessageType.TABLE_STATE);
            highest.ready(true); shortest.ready(true); middle.ready(true);
            for (int index = 0; index < clients.length; index++) awaitActiveTable(events[index]);
            for (int index = 0; index < 3; index++) {
                long viewerId = ids[index];
                assertEquals(1, clients[index].state().table().seats().stream().filter(seat -> seat.holeCards().size() == 2).count());
                assertTrue(clients[index].state().table().seats().stream().filter(seat -> seat.userId() != viewerId)
                    .allMatch(seat -> seat.holeCards().isEmpty()), "Players must not see another player's hole cards before showdown");
            }
            assertEquals(3, observer.state().table().seats().stream().filter(seat -> seat.holeCards().size() == 2).count());
            // With three seats, dealer/highest stack acts first, followed clockwise by the two blinds.
            for (int index = 0; index < 3; index++) {
                Message prompt = events[index].await(MessageType.ACTION_REQUIRED);
                var state = clients[index].state().table();
                assertEquals(ids[index], state.currentTurnUserId().longValue()); assertEquals(prompt.seq(), state.stateVersion());
                clients[index].action(index == 0 ? "ALL_IN" : "CALL", 0, state);
                if (index < 2) events[index].await(MessageType.ACTION_ACCEPTED);
            }
            Message result = events[3].await(MessageType.HAND_RESULT);
            assertEquals("SHOWDOWN", result.payload().path("reason").asText());
            assertEquals(3, result.payload().path("showdown").size());
            assertEquals(2, result.payload().path("pots").size());
            assertEquals(9_000, result.payload().path("pots").get(0).path("amount").asLong());
            assertEquals(8_000, result.payload().path("pots").get(1).path("amount").asLong());
            assertEquals(1, result.payload().path("uncalledReturns").size());
            assertEquals(ids[0], result.payload().path("uncalledReturns").get(0).path("userId").asLong());
            assertEquals(3_000, result.payload().path("uncalledReturns").get(0).path("amount").asLong());
            awaitTableStatus(events[3], "BETWEEN_HANDS");
            assertEquals(5, observer.state().table().board().size());
            assertEquals(20_000, observer.state().table().seats().stream().mapToLong(seat -> seat.stack()).sum());
            long persistedTotal = 0;
            for (int index = 0; index < 3; index++) {
                long viewerId = ids[index];
                Message playerResult = events[index].await(MessageType.HAND_RESULT);
                assertEquals(3, playerResult.payload().path("showdown").size());
                awaitTableStatus(events[index], "BETWEEN_HANDS");
                long balance = users.findById(ids[index]).orElseThrow().chips();
                persistedTotal += balance;
                assertEquals(balance, clients[index].state().chips(), "Settlement must reach each player's wallet snapshot");
                assertNotNull(clients[index].state().glory());
                var persistedUser = users.findById(ids[index]).orElseThrow();
                assertEquals(com.poker.model.GlorySummary.from(balance, persistedUser.wins(), persistedUser.gamesPlayed()),
                    clients[index].state().glory(), "Public glory must agree with committed DB settlement");
                assertEquals(balance, clients[index].state().table().seats().stream()
                    .filter(seat -> seat.userId() == viewerId).findFirst().orElseThrow().stack());
            }
            assertEquals(20_000, persistedTotal, "Side-pot payout plus uncalled return must conserve all starting chips");
        } finally {
            server.close(); serverThread.join(5_000);
            if (tableId != null) try (var connection = database.open()) {
                for (String sql : java.util.List.of(
                    "DELETE FROM wallet_transactions WHERE hand_id IN (SELECT id FROM hands WHERE table_id=?)",
                    "DELETE FROM hand_actions WHERE hand_id IN (SELECT id FROM hands WHERE table_id=?)",
                    "DELETE FROM hand_players WHERE hand_id IN (SELECT id FROM hands WHERE table_id=?)",
                    "DELETE FROM hands WHERE table_id=?", "DELETE FROM poker_tables WHERE id=?")) {
                    try (var statement = connection.prepareStatement(sql)) { statement.setString(1, tableId); statement.executeUpdate(); }
                }
            }
            cleanupUsers(database, names);
        }
    }

    @Test void wrongTableAndHandActionEnvelopesAreRejected() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 20);
        PokerServer server = new PokerServer(config, new DatabaseManager(config));
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        var codec = new com.poker.protocol.ProtocolCodec();
        TestListener guestEvents = new TestListener();
        try (var socket = new java.net.Socket("127.0.0.1", server.localPort()); PokerClient guest = new PokerClient(guestEvents)) {
            socket.setSoTimeout(5_000);
            var input = new java.io.DataInputStream(socket.getInputStream());
            var output = new java.io.DataOutputStream(socket.getOutputStream());
            codec.write(output, Message.of(MessageType.REGISTER, codec.mapper().createObjectNode()
                .put("username", username()).put("password", "owner-password")));
            readUntil(codec, input, MessageType.LOGIN_SUCCESS);
            codec.write(output, Message.of(MessageType.CREATE_TABLE, codec.mapper().createObjectNode().put("smallBlind", 50).put("maxPlayers", 2)));
            String tableId = readUntil(codec, input, MessageType.TABLE_STATE).payload().path("tableId").asText();
            guest.connect("127.0.0.1", server.localPort()); guest.register(username(), "guest-password"); guestEvents.await(MessageType.LOGIN_SUCCESS);
            guest.joinTable(tableId); guestEvents.await(MessageType.TABLE_STATE);
            codec.write(output, Message.of(MessageType.READY, codec.mapper().createObjectNode())); guest.ready(true);
            Message table;
            do { table = readUntil(codec, input, MessageType.TABLE_STATE); }
            while (!"IN_HAND".equals(table.payload().path("status").asText()) ||
                !"BETTING".equals(table.payload().path("phase").asText()));
            var fold = codec.mapper().createObjectNode().put("action", "FOLD").put("amount", 0);
            codec.write(output, new Message(1, MessageType.PLAYER_ACTION, UUID.randomUUID().toString(), null,
                "wrong-table", table.handId(), table.seq(), System.currentTimeMillis(), fold));
            assertEquals("STALE_STATE", readUntil(codec, input, MessageType.ACTION_REJECTED).payload().path("code").asText());
            codec.write(output, new Message(1, MessageType.PLAYER_ACTION, UUID.randomUUID().toString(), null,
                tableId, "wrong-hand", table.seq(), System.currentTimeMillis(), fold));
            assertEquals("STALE_STATE", readUntil(codec, input, MessageType.ACTION_REJECTED).payload().path("code").asText());
            codec.write(output, new Message(1, MessageType.PLAYER_ACTION, UUID.randomUUID().toString(), null,
                tableId, table.handId(), table.seq(), System.currentTimeMillis(), fold));
            assertEquals(1, readUntil(codec, input, MessageType.HAND_RESULT).payload().path("winners").size());
        } finally { server.close(); serverThread.join(5_000); }
    }

    private Message readUntil(com.poker.protocol.ProtocolCodec codec, java.io.DataInputStream input, MessageType type) throws Exception {
        Message message;
        do { message = codec.read(input); } while (message.type() != type);
        return message;
    }

    @Test void passwordResetAndLoginCannotAttachUsingTheRevokedPassword() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 10);
        DatabaseManager database = new DatabaseManager(config);
        String playerName = username(), adminName = username();
        var oldHash = new PasswordHasher().hash("old-password".toCharArray());
        long playerId = new JdbcUserRepository(database).create(playerName, oldHash.hash(), oldHash.salt(), oldHash.iterations(), 10_000).id();
        new JdbcAccountAdministration(database, new ObjectMapper()).createAdmin(adminName, oldHash.hash(), oldHash.salt(), oldHash.iterations());
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        TestListener adminEvents = new TestListener(), playerEvents = new TestListener();
        try (PokerClient admin = new PokerClient(adminEvents); PokerClient player = new PokerClient(playerEvents)) {
            admin.connect("127.0.0.1", server.localPort()); admin.login(adminName, "old-password"); adminEvents.await(MessageType.LOGIN_SUCCESS);
            player.connect("127.0.0.1", server.localPort());
            try (var blocker = database.open(); var lock = blocker.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                blocker.setAutoCommit(false); lock.setLong(1, playerId);
                try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
                admin.adminAccountAction(playerId, "RESET_PASSWORD", null, "new-password", 0, null);
                Thread.sleep(250); player.login(playerName, "old-password"); Thread.sleep(150); blocker.commit();
            }
            assertEquals("LOGIN_FAILED", playerEvents.await(MessageType.ERROR).payload().path("code").asText());
            player.login(playerName, "new-password"); playerEvents.await(MessageType.LOGIN_SUCCESS);
        } finally { server.close(); serverThread.join(5_000); }
    }

    @Test void joiningDuringCoinApprovalUsesTheCommittedWallet() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 10);
        DatabaseManager database = new DatabaseManager(config);
        String playerName = username(), hostName = username(), adminName = username();
        var hash = new PasswordHasher().hash("admin-password".toCharArray());
        new JdbcAccountAdministration(database, new ObjectMapper()).createAdmin(adminName, hash.hash(), hash.salt(), hash.iterations());
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        TestListener playerEvents = new TestListener(), hostEvents = new TestListener(), adminEvents = new TestListener();
        try (PokerClient player = new PokerClient(playerEvents); PokerClient host = new PokerClient(hostEvents);
             PokerClient admin = new PokerClient(adminEvents)) {
            player.connect("127.0.0.1", server.localPort()); host.connect("127.0.0.1", server.localPort());
            admin.connect("127.0.0.1", server.localPort());
            player.register(playerName, "player-password"); host.register(hostName, "host-password"); admin.login(adminName, "admin-password");
            long playerId = playerEvents.await(MessageType.LOGIN_SUCCESS).payload().path("userId").asLong();
            hostEvents.await(MessageType.LOGIN_SUCCESS); adminEvents.await(MessageType.LOGIN_SUCCESS);
            host.createTable(50, 2); hostEvents.await(MessageType.TABLE_STATE);
            player.submitCoinRequest(5_000);
            long requestId = playerEvents.await(MessageType.COIN_REQUEST_SNAPSHOT).payload().get(0).path("id").asLong();
            awaitPendingRequest(adminEvents, playerName);
            try (var blocker = database.open(); var lock = blocker.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                blocker.setAutoCommit(false); lock.setLong(1, playerId);
                try (var rows = lock.executeQuery()) { assertTrue(rows.next()); }
                admin.reviewCoinRequest(requestId, true, "race regression");
                Thread.sleep(250);
                player.joinTable(host.state().table().tableId());
                Thread.sleep(150); blocker.commit();
            }
            assertEquals(15_000, playerEvents.await(MessageType.WALLET_UPDATED).payload().path("chips").asLong());
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (player.state().table() == null && System.nanoTime() < deadline) Thread.sleep(20);
            assertNotNull(player.state().table());
            var ownSeat = player.state().table().seats().stream()
                .filter(seat -> seat.userId() == playerId).findFirst().orElseThrow();
            assertEquals(15_000, ownSeat.stack(), "Table stack must use the wallet read inside the shared user lock");
        } finally { server.close(); serverThread.join(5_000); }
    }

    @Test void passwordLoginReconnectsTheExistingSeat() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        DatabaseManager database = new DatabaseManager(new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 10));
        PokerServer server = new PokerServer(new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 10), database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        String name = username();
        TestListener events = new TestListener(), guestEvents = new TestListener();
        try (PokerClient original = new PokerClient(events); PokerClient guest = new PokerClient(guestEvents)) {
            original.connect("127.0.0.1", server.localPort()); original.register(name, "seat-password");
            events.await(MessageType.LOGIN_SUCCESS);
            original.createTable(50, 2); events.await(MessageType.TABLE_STATE);
            String tableId = original.state().table().tableId();
            guest.connect("127.0.0.1", server.localPort()); guest.register(username(), "guest-password"); guestEvents.await(MessageType.LOGIN_SUCCESS);
            guest.joinTable(tableId); guestEvents.await(MessageType.TABLE_STATE);
            original.ready(true); guest.ready(true);
            awaitActiveTable(events); awaitActiveTable(guestEvents);
            original.close();
            Thread.sleep(150);
            TestListener loginEvents = new TestListener();
            try (PokerClient returning = new PokerClient(loginEvents)) {
                returning.connect("127.0.0.1", server.localPort()); returning.login(name, "seat-password");
                loginEvents.await(MessageType.LOGIN_SUCCESS);
                Message snapshot = loginEvents.await(MessageType.STATE_SNAPSHOT);
                assertEquals(tableId, snapshot.payload().path("tableId").asText());
                assertTrue(snapshot.payload().path("seats").get(0).path("connected").asBoolean(),
                    "Password login must reconnect its existing seat, just as session resume does");
                String token = returning.state().sessionId();
                returning.logout(); Thread.sleep(150);
                TestListener afterLogoutEvents = new TestListener();
                try (PokerClient afterLogout = new PokerClient(afterLogoutEvents)) {
                    afterLogout.connect("127.0.0.1", server.localPort()); afterLogout.resume(token);
                    assertEquals("SESSION_EXPIRED", afterLogoutEvents.await(MessageType.ERROR).payload().path("code").asText());
                    afterLogout.login(name, "seat-password"); afterLogoutEvents.await(MessageType.LOGIN_SUCCESS);
                    afterLogout.requestLobby();
                    assertFalse(afterLogoutEvents.await(MessageType.LOBBY_SNAPSHOT).payload().path("seatReleasePending").asBoolean(),
                        "Explicit logout must release the seat rather than leave a ghost player");
                }
            }
        } finally { server.close(); serverThread.join(5_000); }
    }

    @Test void disconnectResumeAndObserverProjectionKeepTheSameHand() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 10);
        DatabaseManager database = new DatabaseManager(config);
        String firstName = username(), secondName = username(), observerName = username();
        var hash = new PasswordHasher().hash("observer-password".toCharArray());
        long observerId = new JdbcUserRepository(database).create(observerName, hash.hash(), hash.salt(), hash.iterations(), 10_000).id();
        try (var connection = database.open(); var statement = connection.prepareStatement("UPDATE users SET account_role='OBSERVER' WHERE id=?")) {
            statement.setLong(1, observerId); statement.executeUpdate();
        }
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        TestListener firstEvents = new TestListener(), secondEvents = new TestListener(), observerEvents = new TestListener();
        PokerClient first = new PokerClient(firstEvents);
        try (PokerClient second = new PokerClient(secondEvents); PokerClient observer = new PokerClient(observerEvents)) {
            first.connect("127.0.0.1", server.localPort()); second.connect("127.0.0.1", server.localPort());
            observer.connect("127.0.0.1", server.localPort());
            first.register(firstName, "first-password"); second.register(secondName, "second-password");
            observer.login(observerName, "observer-password");
            Message firstLogin = firstEvents.await(MessageType.LOGIN_SUCCESS);
            secondEvents.await(MessageType.LOGIN_SUCCESS);
            assertEquals("OBSERVER", observerEvents.await(MessageType.LOGIN_SUCCESS).payload().path("accountRole").asText());

            first.createTable(50, 2); firstEvents.await(MessageType.TABLE_STATE);
            String tableId = first.state().table().tableId();
            second.joinTable(tableId); secondEvents.await(MessageType.TABLE_STATE);
            observer.spectate(tableId); observerEvents.await(MessageType.TABLE_STATE);
            observer.action("FOLD", 0);
            assertEquals("OBSERVER_CANNOT_ACT", observerEvents.await(MessageType.ACTION_REJECTED).payload().path("code").asText());
            first.ready(true); second.ready(true);
            awaitActiveTable(firstEvents); awaitActiveTable(secondEvents); awaitActiveTable(observerEvents);
            assertEquals(1, first.state().table().seats().stream().filter(seat -> seat.holeCards().size() == 2).count());
            assertEquals(2, observer.state().table().seats().stream().filter(seat -> seat.holeCards().size() == 2).count());
            String token = firstLogin.payload().path("sessionId").asText();
            first.close();
            TestListener resumedEvents = new TestListener();
            try (PokerClient resumed = new PokerClient(resumedEvents)) {
                resumed.connect("127.0.0.1", server.localPort()); resumed.resume(token);
                assertEquals(firstLogin.payload().path("userId").asLong(),
                    resumedEvents.await(MessageType.LOGIN_SUCCESS).payload().path("userId").asLong());
                Message snapshot = resumedEvents.await(MessageType.STATE_SNAPSHOT);
                assertEquals(tableId, snapshot.payload().path("tableId").asText());
                assertEquals("IN_HAND", snapshot.payload().path("status").asText());
                assertEquals(1, java.util.stream.StreamSupport.stream(snapshot.payload().path("seats").spliterator(), false)
                    .filter(seat -> seat.path("holeCards").size() == 2).count());
            }
        } finally {
            first.close(); server.close(); serverThread.join(5_000);
        }
    }

    @Test void playerCoinRequestAdminApprovalAndRbacWorkOverTcp() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 2);
        DatabaseManager database = new DatabaseManager(config);
        String playerName = username(), adminName = "admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var hash = new PasswordHasher().hash("admin-password".toCharArray());
        new JdbcAccountAdministration(database, new ObjectMapper()).createAdmin(adminName, hash.hash(), hash.salt(), hash.iterations());
        PokerServer server = new PokerServer(config, database);
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Exception ignored) { } });
        awaitPort(server);
        TestListener playerEvents = new TestListener(), adminEvents = new TestListener();
        try (PokerClient player = new PokerClient(playerEvents); PokerClient admin = new PokerClient(adminEvents)) {
            player.connect("127.0.0.1", server.localPort()); admin.connect("127.0.0.1", server.localPort());
            player.register(playerName, "player-password"); admin.login(adminName, "admin-password");
            Message playerLogin = playerEvents.await(MessageType.LOGIN_SUCCESS);
            Message adminLogin = adminEvents.await(MessageType.LOGIN_SUCCESS);
            assertEquals("PLAYER", playerLogin.payload().path("accountRole").asText());
            assertEquals("ADMIN", adminLogin.payload().path("accountRole").asText());

            player.requestAdminDashboard();
            assertEquals("UNAUTHORIZED", playerEvents.await(MessageType.ERROR).payload().path("code").asText());
            admin.createTable(50, 6);
            assertEquals("UNAUTHORIZED", adminEvents.await(MessageType.ERROR).payload().path("code").asText());

            player.submitCoinRequest(5_000);
            assertEquals("PENDING", playerEvents.await(MessageType.COIN_REQUEST_SNAPSHOT).payload().get(0).path("status").asText());
            Message dashboard = awaitPendingRequest(adminEvents, playerName);
            long requestId = java.util.stream.StreamSupport.stream(dashboard.payload().path("requests").spliterator(), false)
                .filter(item -> playerName.equals(item.path("username").asText()) && "PENDING".equals(item.path("status").asText()))
                .findFirst().orElseThrow().path("id").asLong();
            admin.reviewCoinRequest(requestId, true, "approved in e2e");
            assertEquals(15_000, playerEvents.await(MessageType.WALLET_UPDATED).payload().path("chips").asLong());
            assertEquals(15_000, new JdbcUserRepository(database).findByUsername(playerName).orElseThrow().chips());

            long playerId = playerLogin.payload().path("userId").asLong();
            admin.adminAccountAction(playerId, "LOCK", null, null, 0, null);
            assertEquals(MessageType.ACCOUNT_LOCKED, playerEvents.await(MessageType.ACCOUNT_LOCKED).type());
        } finally {
            server.close(); serverThread.join(5_000); cleanupUsers(database, playerName, adminName);
        }
    }

    @Test void twoClientsPlayOneHandOverRealTcpAndPersistSettlement() throws Exception {
        String url = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(url != null && url.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(url, dbUser, dbPassword);
        AppConfig config = new AppConfig("127.0.0.1", 0, url, dbUser, dbPassword, 2);
        DatabaseManager database = new DatabaseManager(config);
        PokerServer server = new PokerServer(config, database);
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread serverThread = Thread.ofPlatform().start(() -> { try { server.start(); } catch (Throwable error) { serverFailure.set(error); } });
        awaitPort(server);

        TestListener firstEvents = new TestListener(), secondEvents = new TestListener(), payoutAdminEvents = new TestListener();
        String firstName = username(), secondName = username(), payoutAdminName = username();
        var adminHash = new PasswordHasher().hash("payout-admin-pass".toCharArray());
        new JdbcAccountAdministration(database, new ObjectMapper()).createAdmin(payoutAdminName, adminHash.hash(), adminHash.salt(), adminHash.iterations());
        try (PokerClient first = new PokerClient(firstEvents); PokerClient second = new PokerClient(secondEvents); PokerClient payoutAdmin = new PokerClient(payoutAdminEvents)) {
            payoutAdmin.connect("127.0.0.1", server.localPort()); payoutAdmin.login(payoutAdminName, "payout-admin-pass"); payoutAdminEvents.await(MessageType.LOGIN_SUCCESS);
            first.connect("127.0.0.1", server.localPort()); second.connect("127.0.0.1", server.localPort());
            first.register(firstName, "password-one"); second.register(secondName, "password-two");
            Message firstLogin = firstEvents.await(MessageType.LOGIN_SUCCESS);
            Message secondLogin = secondEvents.await(MessageType.LOGIN_SUCCESS);
            long firstId = firstLogin.payload().path("userId").asLong(), secondId = secondLogin.payload().path("userId").asLong();

            first.createTable(50, 6); firstEvents.await(MessageType.TABLE_STATE);
            String tableId = first.state().table().tableId();
            second.joinTable(tableId); secondEvents.await(MessageType.TABLE_STATE);
            first.ready(true); second.ready(true);

            awaitActiveTable(firstEvents);
            awaitActiveTable(secondEvents);
            var snapshot = first.state().table();
            PokerClient actor = snapshot.currentTurnUserId() == firstId ? first : second;
            TestListener actorEvents = snapshot.currentTurnUserId() == firstId ? firstEvents : secondEvents;
            actor.action("FOLD", 0);
            Message result = actorEvents.await(MessageType.HAND_RESULT);
            assertEquals(1, result.payload().path("winners").size());
            assertTrue(result.payload().path("winners").get(0).path("amount").asLong() > 0);
            long winnerId = result.payload().path("winners").get(0).path("userId").asLong();
            assertEquals(100, result.payload().path("pots").get(0).path("amount").asLong());
            assertEquals(50, result.payload().path("uncalledReturns").get(0).path("amount").asLong());
            assertEquals(winnerId, result.payload().path("uncalledReturns").get(0).path("userId").asLong());
            boolean adminSawPayout = false;
            while (!adminSawPayout) {
                Message dashboard = payoutAdminEvents.await(MessageType.ADMIN_DASHBOARD);
                for (var user : dashboard.payload().path("users"))
                    if (user.path("id").asLong() == winnerId && user.path("chips").asLong() == 10050) adminSawPayout = true;
            }

            JdbcUserRepository users = new JdbcUserRepository(database);
            var firstAccount = users.findById(firstId).orElseThrow();
            var secondAccount = users.findById(secondId).orElseThrow();
            assertEquals(20_000, firstAccount.chips() + secondAccount.chips());
            assertEquals(10_050, users.findById(winnerId).orElseThrow().chips());
            assertEquals(9_950, users.findById(winnerId == firstId ? secondId : firstId).orElseThrow().chips());
            assertEquals(1, firstAccount.gamesPlayed());
            assertEquals(1, secondAccount.gamesPlayed());

            awaitTableStatus(firstEvents, "BETWEEN_HANDS");
            awaitTableStatus(secondEvents, "BETWEEN_HANDS");
            assertEquals(firstAccount.chips(), first.state().chips());
            assertEquals(secondAccount.chips(), second.state().chips());
            first.ready(true); second.ready(true);
            awaitActiveTable(firstEvents);
            assertEquals("IN_HAND", first.state().table().status().name());

            first.requestHistory();
            Message history = firstEvents.await(MessageType.HISTORY_SNAPSHOT);
            assertEquals(1, history.payload().size());
            assertTrue(history.payload().get(0).path("startedAt").isTextual());

            first.login(secondName, "password-two");
            Message rejected = firstEvents.await(MessageType.ERROR);
            assertEquals("INVALID_REQUEST", rejected.payload().path("code").asText());
        } finally {
            server.close(); serverThread.join(5_000);
        }
        assertNull(serverFailure.get());
    }

    private void awaitPort(PokerServer server) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (server.localPort() < 0 && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue(server.localPort() > 0, "Server did not bind a port");
    }

    private Message awaitPendingRequest(TestListener listener, String username) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            Message dashboard = listener.await(MessageType.ADMIN_DASHBOARD);
            if (java.util.stream.StreamSupport.stream(dashboard.payload().path("requests").spliterator(), false)
                .anyMatch(item -> username.equals(item.path("username").asText()) && "PENDING".equals(item.path("status").asText()))) return dashboard;
        }
        throw new AssertionError("Pending request not pushed to Admin");
    }

    private void awaitActiveTable(TestListener listener) throws Exception {
        awaitTableStatus(listener, "IN_HAND");
    }

    private void awaitTableStatus(TestListener listener, String status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            Message message = listener.events.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (message != null && message.type() == MessageType.TABLE_STATE &&
                status.equals(message.payload().path("status").asText()) &&
                (!"IN_HAND".equals(status) || !message.payload().path("currentTurnUserId").isNull())) return;
        }
        fail(status + " table state was not received");
    }

    private void initialize(String url, String user, String password) throws Exception {
        String sql = Files.readString(Path.of("database", "schema.sql")).replace("poker_game", "poker_test");
        String migration = Files.readString(Path.of("database", "migrations", "V2__account_roles_and_coin_requests.sql"))
            .replace("poker_game", "poker_test");
        String serverUrl = url.replaceFirst("/poker_test(?:[?].*)?$", "/mysql");
        try (var connection = DriverManager.getConnection(serverUrl, user, password); var statement = connection.createStatement()) {
            for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part);
            for (String part : migration.split(";")) if (!part.isBlank()) statement.execute(part);
        }
    }

    private String username() { return "e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10); }
    private String value(String name, String fallback) { String value = System.getenv(name); return value == null ? fallback : value; }

    private void cleanupUsers(DatabaseManager database, String... usernames) throws Exception {
        try (var connection = database.open()) {
            for (String username : usernames) {
                long id;
                try (var find = connection.prepareStatement("SELECT id FROM users WHERE username=?")) {
                    find.setString(1, username); try (var rows = find.executeQuery()) { if (!rows.next()) continue; id = rows.getLong(1); }
                }
                for (String sql : java.util.List.of(
                    "DELETE FROM admin_audit_log WHERE admin_user_id=? OR target_user_id=?",
                    "DELETE FROM wallet_transactions WHERE user_id=? OR admin_user_id=?")) {
                    try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, id); statement.setLong(2, id); statement.executeUpdate(); }
                }
                try (var statement = connection.prepareStatement("DELETE FROM coin_requests WHERE user_id=? OR reviewed_by=?")) { statement.setLong(1, id); statement.setLong(2, id); statement.executeUpdate(); }
                try (var statement = connection.prepareStatement("DELETE FROM sessions WHERE user_id=?")) { statement.setLong(1, id); statement.executeUpdate(); }
                try (var statement = connection.prepareStatement("DELETE FROM users WHERE id=?")) { statement.setLong(1, id); statement.executeUpdate(); }
            }
        }
    }

    private static final class TestListener implements ClientEventListener {
        private final LinkedBlockingQueue<Message> events = new LinkedBlockingQueue<>();
        @Override public void onMessage(Message message) { events.add(message); }
        Message await(MessageType type) throws Exception {
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            java.util.List<String> seen = new java.util.ArrayList<>();
            while (System.nanoTime() < deadline) {
                Message message = events.poll(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (message != null && message.type() == type) return message;
                if (message != null) seen.add(message.type() + ":" + message.payload().path("code").asText());
            }
            throw new AssertionError("Did not receive " + type + "; saw " + seen);
        }
    }
}
