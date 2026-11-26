package com.poker.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poker.common.PasswordHasher;
import com.poker.model.AccountRole;
import com.poker.model.AccountStatus;
import com.poker.model.CoinRequestStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class JdbcAccountAdministration {
    private final DatabaseManager database;
    private final ObjectMapper mapper;
    private final PasswordHasher passwords = new PasswordHasher();

    public JdbcAccountAdministration(DatabaseManager database, ObjectMapper mapper) {
        this.database = database; this.mapper = mapper;
    }

    public void submitCoinRequest(long userId, long amount) throws SQLException {
        if (amount < 1 || amount > 100_000) throw new IllegalArgumentException("Coin amount must be 1 to 100000");
        transaction(connection -> {
            UserRow user = lockUser(connection, userId);
            if (user.role != AccountRole.PLAYER || user.status != AccountStatus.ACTIVE)
                throw new IllegalStateException("Only active players can request coins");
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM coin_requests WHERE user_id=? AND status='PENDING' FOR UPDATE")) {
                statement.setLong(1, userId);
                try (ResultSet result = statement.executeQuery()) { if (result.next()) throw new IllegalStateException("A pending coin request already exists"); }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO coin_requests(user_id,amount,status) VALUES(?,?,'PENDING')")) {
                statement.setLong(1, userId); statement.setLong(2, amount); statement.executeUpdate();
            }
            return null;
        });
    }

    public List<CoinRequestView> requestsFor(long userId) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT cr.id,cr.user_id,u.username,cr.amount,cr.status,cr.note,cr.requested_at,cr.reviewed_at " +
                "FROM coin_requests cr JOIN users u ON u.id=cr.user_id WHERE cr.user_id=? ORDER BY cr.requested_at DESC")) {
            statement.setLong(1, userId); return readRequests(statement);
        }
    }

    public long requestOwner(long requestId) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT user_id FROM coin_requests WHERE id=?")) {
            statement.setLong(1, requestId); try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Coin request not found"); return result.getLong(1);
            }
        }
    }

    public Dashboard dashboard(Set<Long> onlineUsers) throws SQLException {
        List<AdminUserView> users = new ArrayList<>();
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT id,username,account_role,account_status,chips,wins,games_played FROM users ORDER BY username");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) users.add(new AdminUserView(result.getLong(1), result.getString(2),
                AccountRole.valueOf(result.getString(3)), AccountStatus.valueOf(result.getString(4)), result.getLong(5),
                result.getLong(6), result.getLong(7), onlineUsers.contains(result.getLong(1))));
        }
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT cr.id,cr.user_id,u.username,cr.amount,cr.status,cr.note,cr.requested_at,cr.reviewed_at " +
                "FROM coin_requests cr JOIN users u ON u.id=cr.user_id ORDER BY (cr.status='PENDING') DESC,cr.requested_at DESC")) {
            return new Dashboard(users, readRequests(statement));
        }
    }

    public long review(long adminId, long requestId, boolean approve, String note) throws SQLException {
        String cleaned = cleanNote(note, !approve);
        return transaction(connection -> {
            requireAdmin(connection, adminId);
            long userId, amount;
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id,amount,status FROM coin_requests WHERE id=? FOR UPDATE")) {
                statement.setLong(1, requestId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new IllegalArgumentException("Coin request not found");
                    if (!CoinRequestStatus.PENDING.name().equals(result.getString(3))) throw new IllegalStateException("Coin request was already reviewed");
                    userId = result.getLong(1); amount = result.getLong(2);
                }
            }
            UserRow target = lockUser(connection, userId);
            if (approve && (target.role != AccountRole.PLAYER || target.status != AccountStatus.ACTIVE))
                throw new IllegalStateException("Target account is not an active player");
            if (approve) {
                long balance = Math.addExact(target.chips, amount);
                updateBalance(connection, userId, balance);
                try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO wallet_transactions(hand_id,coin_request_id,admin_user_id,user_id,transaction_type,amount,balance_after,reason) " +
                        "VALUES(NULL,?,?,?,'COIN_REQUEST_APPROVED',?,?,?)")) {
                    statement.setLong(1, requestId); statement.setLong(2, adminId); statement.setLong(3, userId);
                    statement.setLong(4, amount); statement.setLong(5, balance); statement.setString(6, cleaned); statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE coin_requests SET status=?,reviewed_by=?,note=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?")) {
                statement.setString(1, approve ? "APPROVED" : "REJECTED"); statement.setLong(2, adminId);
                statement.setString(3, cleaned); statement.setLong(4, requestId); statement.executeUpdate();
            }
            audit(connection, adminId, userId, approve ? "APPROVE_COIN" : "REJECT_COIN",
                mapper.createObjectNode().put("requestId", requestId).put("amount", amount).put("note", cleaned).toString());
            return userId;
        });
    }

    public void setStatus(long adminId, long userId, AccountStatus status) throws SQLException {
        transaction(connection -> { requireMutableTarget(connection, adminId, userId);
            update(connection, "UPDATE users SET account_status=? WHERE id=?", status.name(), userId);
            audit(connection, adminId, userId, status.name(), "{}"); return null; });
    }

    public void setRole(long adminId, long userId, AccountRole role) throws SQLException {
        if (role == AccountRole.ADMIN) throw new IllegalArgumentException("Admin role is created only by the host tool");
        transaction(connection -> { requireMutableTarget(connection, adminId, userId);
            try (PreparedStatement pending = connection.prepareStatement("SELECT id FROM coin_requests WHERE user_id=? AND status='PENDING' FOR UPDATE")) {
                pending.setLong(1, userId); try (ResultSet result = pending.executeQuery()) { if (result.next()) throw new IllegalStateException("Reject the pending coin request before changing role"); }
            }
            update(connection, "UPDATE users SET account_role=? WHERE id=?", role.name(), userId);
            audit(connection, adminId, userId, "SET_ROLE", mapper.createObjectNode().put("role", role.name()).toString()); return null; });
    }

    public void resetPassword(long adminId, long userId, char[] password) throws SQLException {
        var hashed = passwords.hash(password);
        transaction(connection -> { requireMutableTarget(connection, adminId, userId);
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET password_hash=?,password_salt=?,password_iterations=? WHERE id=?")) {
                statement.setBytes(1, hashed.hash()); statement.setBytes(2, hashed.salt()); statement.setInt(3, hashed.iterations());
                statement.setLong(4, userId); statement.executeUpdate();
            }
            audit(connection, adminId, userId, "RESET_PASSWORD", "{}"); return null; });
    }

    public long adjustCoins(long adminId, long userId, long delta, String reason) throws SQLException {
        if (delta == 0) throw new IllegalArgumentException("Coin delta cannot be zero");
        String cleaned = cleanNote(reason, true);
        return transaction(connection -> { requireAdmin(connection, adminId); UserRow target = lockUser(connection, userId);
            if (target.role == AccountRole.ADMIN) throw new IllegalStateException("Admin accounts cannot be edited here");
            long balance = Math.addExact(target.chips, delta); if (balance < 0) throw new IllegalArgumentException("Balance cannot be negative");
            updateBalance(connection, userId, balance);
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO wallet_transactions(hand_id,admin_user_id,user_id,transaction_type,amount,balance_after,reason) VALUES(NULL,?,?,'ADMIN_ADJUSTMENT',?,?,?)")) {
                statement.setLong(1, adminId); statement.setLong(2, userId); statement.setLong(3, delta);
                statement.setLong(4, balance); statement.setString(5, cleaned); statement.executeUpdate();
            }
            audit(connection, adminId, userId, "ADJUST_COINS", mapper.createObjectNode().put("delta", delta).put("reason", cleaned).toString());
            return balance;
        });
    }

    public void createAdmin(String username, byte[] hash, byte[] salt, int iterations) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO users(username,password_hash,password_salt,password_iterations,account_role,account_status,chips) VALUES(?,?,?,?,'ADMIN','ACTIVE',0)")) {
            statement.setString(1, username); statement.setBytes(2, hash); statement.setBytes(3, salt); statement.setInt(4, iterations); statement.executeUpdate();
        }
    }

    private UserRow requireMutableTarget(Connection connection, long adminId, long userId) throws SQLException {
        requireAdmin(connection, adminId); UserRow target = lockUser(connection, userId);
        if (target.role == AccountRole.ADMIN) throw new IllegalStateException("Admin accounts cannot be edited here"); return target;
    }
    private void requireAdmin(Connection connection, long userId) throws SQLException {
        UserRow row = lockUser(connection, userId);
        if (row.role != AccountRole.ADMIN || row.status != AccountStatus.ACTIVE) throw new IllegalStateException("Admin authorization required");
    }
    private UserRow lockUser(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT account_role,account_status,chips FROM users WHERE id=? FOR UPDATE")) {
            statement.setLong(1, id); try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("User not found");
                return new UserRow(AccountRole.valueOf(result.getString(1)), AccountStatus.valueOf(result.getString(2)), result.getLong(3));
            }
        }
    }
    private void updateBalance(Connection connection, long userId, long balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET chips=? WHERE id=?")) {
            statement.setLong(1, balance); statement.setLong(2, userId); statement.executeUpdate();
        }
    }
    private void update(Connection connection, String sql, String value, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.setString(1, value); statement.setLong(2, userId); statement.executeUpdate(); }
    }
    private void audit(Connection connection, long adminId, long targetId, String action, String details) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO admin_audit_log(admin_user_id,target_user_id,action_type,details_json) VALUES(?,?,?,?)")) {
            statement.setLong(1, adminId); statement.setLong(2, targetId); statement.setString(3, action); statement.setString(4, details); statement.executeUpdate();
        }
    }
    private String cleanNote(String value, boolean required) {
        String note = value == null ? "" : value.trim();
        if (required && (note.length() < 3 || note.length() > 200)) throw new IllegalArgumentException("Reason must contain 3 to 200 characters");
        if (note.length() > 200) throw new IllegalArgumentException("Note is too long"); return note;
    }
    private List<CoinRequestView> readRequests(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            List<CoinRequestView> items = new ArrayList<>();
            while (result.next()) items.add(new CoinRequestView(result.getLong(1), result.getLong(2), result.getString(3),
                result.getLong(4), CoinRequestStatus.valueOf(result.getString(5)), result.getString(6),
                result.getTimestamp(7).toInstant().toString(), result.getTimestamp(8) == null ? null : result.getTimestamp(8).toInstant().toString()));
            return items;
        }
    }
    private <T> T transaction(SqlWork<T> work) throws SQLException {
        try (Connection connection = database.open()) { connection.setAutoCommit(false);
            try { T result = work.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
            finally { connection.setAutoCommit(true); }
        }
    }
    private record UserRow(AccountRole role, AccountStatus status, long chips) {}
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    public record CoinRequestView(long id, long userId, String username, long amount, CoinRequestStatus status,
                                  String note, String requestedAt, String reviewedAt) {}
    public record AdminUserView(long id, String username, AccountRole role, AccountStatus status, long chips,
                                long wins, long gamesPlayed, boolean online) {}
    public record Dashboard(List<AdminUserView> users, List<CoinRequestView> requests) {}
}
