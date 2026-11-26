package com.poker.server.session;

import com.poker.persistence.SessionRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.OptionalLong;

public final class SessionManager {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SessionRepository sessions;
    public SessionManager(SessionRepository sessions) { this.sessions = sessions; }

    public String create(long userId) throws SQLException {
        byte[] token = new byte[32]; RANDOM.nextBytes(token);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        sessions.save(hash(encoded), userId, Instant.now().plus(Duration.ofHours(24)));
        return encoded;
    }

    public OptionalLong resume(String token) throws SQLException {
        return token == null || token.isBlank() ? OptionalLong.empty() : sessions.findActiveUser(hash(token), Instant.now());
    }

    public void revoke(String token) throws SQLException { sessions.revoke(hash(token)); }
    public void revokeAll(long userId) throws SQLException { sessions.revokeAll(userId); }

    private byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
