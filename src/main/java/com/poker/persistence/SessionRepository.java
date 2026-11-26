package com.poker.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.util.OptionalLong;

public interface SessionRepository {
    void save(byte[] tokenHash, long userId, Instant expiresAt) throws SQLException;
    OptionalLong findActiveUser(byte[] tokenHash, Instant now) throws SQLException;
    void revoke(byte[] tokenHash) throws SQLException;
    void revokeAll(long userId) throws SQLException;
}
