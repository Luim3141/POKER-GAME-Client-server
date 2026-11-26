package com.poker.persistence;

import com.poker.common.AppConfig;
import com.poker.common.PasswordHasher;
import com.poker.server.session.SessionManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.model.AccountRole;

import static org.junit.jupiter.api.Assertions.*;

class JdbcPersistenceIT {
    @Test void coinApprovalAndAdminAdjustmentAreTransactional() throws Exception {
        String testUrl = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(testUrl != null && testUrl.matches(".*?/poker_test(?:[?].*)?$"));
        String dbUser = value("POKER_DB_USER", "root"), dbPassword = value("POKER_DB_PASSWORD", "");
        initialize(testUrl, dbUser, dbPassword);
        DatabaseManager database = new DatabaseManager(new AppConfig("127.0.0.1", 19090, testUrl, dbUser, dbPassword, 20));
        JdbcUserRepository users = new JdbcUserRepository(database);
        JdbcAccountAdministration accounts = new JdbcAccountAdministration(database, new ObjectMapper());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var hash = new PasswordHasher().hash("integration-pass".toCharArray());
        long playerId = users.create("player_" + suffix, hash.hash(), hash.salt(), hash.iterations(), 10_000).id();
        accounts.createAdmin("admin_" + suffix, hash.hash(), hash.salt(), hash.iterations());
        long adminId = users.findByUsername("admin_" + suffix).orElseThrow().id();
        try {
            accounts.submitCoinRequest(playerId, 5_000);
            assertThrows(IllegalStateException.class, () -> accounts.submitCoinRequest(playerId, 1_000));
            long requestId = accounts.requestsFor(playerId).getFirst().id();
            assertEquals(playerId, accounts.review(adminId, requestId, true, "approved for test"));
            assertEquals(15_000, users.findById(playerId).orElseThrow().chips());
            assertEquals(0, users.findById(playerId).orElseThrow().wins());
            assertEquals(0, users.findById(playerId).orElseThrow().gamesPlayed());
            assertThrows(IllegalStateException.class, () -> accounts.review(adminId, requestId, true, "duplicate"));
            assertEquals(13_000, accounts.adjustCoins(adminId, playerId, -2_000, "test correction"));
            accounts.setRole(adminId, playerId, AccountRole.OBSERVER);
            assertEquals(AccountRole.OBSERVER, users.findById(playerId).orElseThrow().accountRole());
        } finally { cleanupAccounts(database, playerId, adminId); }
    }

    @Test void userSessionWalletAndSettlementAreTransactionalAndIdempotent() throws Exception {
        String testUrl = System.getenv("POKER_DB_TEST_URL");
        Assumptions.assumeTrue(testUrl != null && testUrl.matches(".*?/poker_test(?:[?].*)?$"),
            "Set POKER_DB_TEST_URL to a dedicated poker_test database");
        String user = value("POKER_DB_USER", "root"), password = value("POKER_DB_PASSWORD", "");
        initialize(testUrl, user, password);
        DatabaseManager database = new DatabaseManager(new AppConfig("127.0.0.1", 19090, testUrl, user, password, 20));
        JdbcUserRepository users = new JdbcUserRepository(database);
        JdbcSessionRepository sessions = new JdbcSessionRepository(database);
        JdbcHandRepository hands = new JdbcHandRepository(database);
        String username = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tableId = UUID.randomUUID().toString(), handId = UUID.randomUUID().toString();
        long userId = -1;
        try {
            var hashed = new PasswordHasher().hash("integration-pass".toCharArray());
            UserAccount account = users.create(username, hashed.hash(), hashed.salt(), hashed.iterations(), 10_000); userId = account.id();
            SessionManager sessionManager = new SessionManager(sessions);
            String token = sessionManager.create(userId);
            assertEquals(userId, sessionManager.resume(token).orElseThrow());
            hands.createTable(tableId, userId, 50, 6);
            HandSettlement settlement = new HandSettlement(handId, tableId, 0, "[]",
                List.of(new HandSettlement.PlayerResult(userId, 0, 10_000, 10_500, 500, true)), List.of());
            assertTrue(hands.settle(settlement));
            assertFalse(hands.settle(settlement));
            assertEquals(10_500, users.findById(userId).orElseThrow().chips());
            assertEquals(1, users.findById(userId).orElseThrow().wins());
            assertEquals(1, users.findById(userId).orElseThrow().gamesPlayed());
            String failedHand = UUID.randomUUID().toString();
            HandSettlement failing = new HandSettlement(failedHand, tableId, 0, "[]",
                List.of(new HandSettlement.PlayerResult(userId, 0, 10_500, 11_000, 500, true),
                    new HandSettlement.PlayerResult(Long.MAX_VALUE, 1, 500, 0, 500, false)), List.of());
            assertThrows(java.sql.SQLException.class, () -> hands.settle(failing));
            UserAccount afterRollback = users.findById(userId).orElseThrow();
            assertEquals(10_500, afterRollback.chips()); assertEquals(1, afterRollback.wins()); assertEquals(1, afterRollback.gamesPlayed());
            assertTrue(users.history(userId, 100).stream().noneMatch(entry -> entry.handId().equals(failedHand)));
        } finally { cleanup(database, handId, tableId, userId); }
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

    private void cleanupAccounts(DatabaseManager database, long playerId, long adminId) throws Exception {
        try (var connection = database.open()) {
            try (var s = connection.prepareStatement("DELETE FROM admin_audit_log WHERE admin_user_id=? OR target_user_id=?")) {
                s.setLong(1, adminId); s.setLong(2, playerId); s.executeUpdate();
            }
            try (var s = connection.prepareStatement("DELETE FROM wallet_transactions WHERE user_id=?")) { s.setLong(1, playerId); s.executeUpdate(); }
            try (var s = connection.prepareStatement("DELETE FROM coin_requests WHERE user_id=?")) { s.setLong(1, playerId); s.executeUpdate(); }
            try (var s = connection.prepareStatement("DELETE FROM users WHERE id IN (?,?)")) { s.setLong(1, playerId); s.setLong(2, adminId); s.executeUpdate(); }
        }
    }

    private void cleanup(DatabaseManager database, String handId, String tableId, long userId) throws Exception {
        if (userId < 0) return;
        try (var connection = database.open()) {
            delete(connection, "DELETE FROM wallet_transactions WHERE hand_id=?", handId);
            delete(connection, "DELETE FROM hand_actions WHERE hand_id=?", handId);
            delete(connection, "DELETE FROM hand_players WHERE hand_id=?", handId);
            delete(connection, "DELETE FROM hands WHERE id=?", handId);
            delete(connection, "DELETE FROM poker_tables WHERE id=?", tableId);
            try (var statement = connection.prepareStatement("DELETE FROM sessions WHERE user_id=?")) { statement.setLong(1, userId); statement.executeUpdate(); }
            try (var statement = connection.prepareStatement("DELETE FROM users WHERE id=?")) { statement.setLong(1, userId); statement.executeUpdate(); }
        }
    }

    private void delete(java.sql.Connection connection, String sql, String id) throws Exception {
        try (var statement = connection.prepareStatement(sql)) { statement.setString(1, id); statement.executeUpdate(); }
    }

    private String value(String name, String fallback) { String value = System.getenv(name); return value == null ? fallback : value; }
}
