package com.poker.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.OptionalLong;

public final class JdbcSessionRepository implements SessionRepository {
    private final DatabaseManager database;
    public JdbcSessionRepository(DatabaseManager database) { this.database = database; }

    @Override public void save(byte[] hash, long userId, Instant expiresAt) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)")) {
            statement.setBytes(1, hash); statement.setLong(2, userId); statement.setTimestamp(3, Timestamp.from(expiresAt)); statement.executeUpdate();
        }
    }

    @Override public OptionalLong findActiveUser(byte[] hash, Instant now) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "SELECT user_id FROM sessions WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?")) {
            statement.setBytes(1, hash); statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) { return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty(); }
        }
    }

    @Override public void revoke(byte[] hash) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "UPDATE sessions SET revoked_at=CURRENT_TIMESTAMP WHERE token_hash=?")) {
            statement.setBytes(1, hash); statement.executeUpdate();
        }
    }

    @Override public void revokeAll(long userId) throws SQLException {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
            "UPDATE sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL")) {
            statement.setLong(1, userId); statement.executeUpdate();
        }
    }
}
