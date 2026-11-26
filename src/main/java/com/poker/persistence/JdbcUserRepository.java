package com.poker.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {
    private final DatabaseManager database;
    public JdbcUserRepository(DatabaseManager database) { this.database = database; }

    @Override public UserAccount create(String username, byte[] hash, byte[] salt, int iterations, long chips) throws SQLException {
        String sql = "INSERT INTO users(username,password_hash,password_salt,password_iterations,chips) VALUES(?,?,?,?,?)";
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username); statement.setBytes(2, hash); statement.setBytes(3, salt);
            statement.setInt(4, iterations); statement.setLong(5, chips); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("User ID was not returned");
                return new UserAccount(keys.getLong(1), username, hash, salt, iterations, chips, 0, 0,
                    com.poker.model.AccountRole.PLAYER, com.poker.model.AccountStatus.ACTIVE);
            }
        }
    }

    @Override public Optional<UserAccount> findByUsername(String username) throws SQLException {
        return find("SELECT * FROM users WHERE username=?", statement -> statement.setString(1, username));
    }

    @Override public Optional<UserAccount> findById(long id) throws SQLException {
        return find("SELECT * FROM users WHERE id=?", statement -> statement.setLong(1, id));
    }

    private Optional<UserAccount> find(String sql, Setter setter) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            setter.set(statement);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(read(result)) : Optional.empty(); }
        }
    }

    @Override public List<RankingEntry> ranking(int limit) throws SQLException {
        String sql = "SELECT username,chips,wins,games_played,CASE WHEN games_played=0 THEN 0 ELSE wins/games_played END win_rate " +
            "FROM users ORDER BY chips DESC,win_rate DESC,wins DESC,username ASC LIMIT ?";
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                List<RankingEntry> entries = new ArrayList<>();
                while (result.next()) entries.add(new RankingEntry(result.getString(1), result.getLong(2), result.getLong(3), result.getLong(4), result.getDouble(5)));
                return entries;
            }
        }
    }

    @Override public List<HistoryEntry> history(long userId, int limit) throws SQLException {
        String sql = "SELECT h.id,h.table_id,hp.result,hp.contribution,hp.starting_stack,hp.final_stack,h.started_at,h.ended_at " +
            "FROM hand_players hp JOIN hands h ON h.id=hp.hand_id WHERE hp.user_id=? ORDER BY h.started_at DESC LIMIT ?";
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId); statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet result = statement.executeQuery()) {
                List<HistoryEntry> entries = new ArrayList<>();
                while (result.next()) entries.add(new HistoryEntry(result.getString(1), result.getString(2), result.getString(3),
                    result.getLong(4), result.getLong(5), result.getLong(6), result.getTimestamp(7).toInstant(),
                    result.getTimestamp(8) == null ? null : result.getTimestamp(8).toInstant()));
                return entries;
            }
        }
    }

    private UserAccount read(ResultSet result) throws SQLException {
        return new UserAccount(result.getLong("id"), result.getString("username"), result.getBytes("password_hash"),
            result.getBytes("password_salt"), result.getInt("password_iterations"), result.getLong("chips"),
            result.getLong("wins"), result.getLong("games_played"),
            com.poker.model.AccountRole.valueOf(result.getString("account_role")),
            com.poker.model.AccountStatus.valueOf(result.getString("account_status")));
    }

    @FunctionalInterface private interface Setter { void set(PreparedStatement statement) throws SQLException; }
}
