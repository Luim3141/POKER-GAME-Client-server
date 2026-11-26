package com.poker.common;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class PasswordHasher {
    public static final int ITERATIONS = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    public HashedPassword hash(char[] password) {
        validate(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new HashedPassword(salt, derive(password, salt, ITERATIONS), ITERATIONS);
    }

    public boolean verify(char[] password, byte[] salt, byte[] expected, int iterations) {
        validate(password);
        return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    }

    private byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password, salt, iterations, 256)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is unavailable", e);
        }
    }

    private void validate(char[] password) {
        if (password == null || password.length < 8 || password.length > 128)
            throw new IllegalArgumentException("Password must contain 8 to 128 characters");
    }

    public record HashedPassword(byte[] salt, byte[] hash, int iterations) {}
}

