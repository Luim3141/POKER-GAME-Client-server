package com.poker.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test void hashesWithSaltAndVerifiesWithoutStoringPlaintext() {
        PasswordHasher hasher = new PasswordHasher();
        var first = hasher.hash("correct horse".toCharArray());
        var second = hasher.hash("correct horse".toCharArray());
        assertFalse(java.util.Arrays.equals(first.hash(), second.hash()));
        assertTrue(hasher.verify("correct horse".toCharArray(), first.salt(), first.hash(), first.iterations()));
        assertFalse(hasher.verify("wrong password".toCharArray(), first.salt(), first.hash(), first.iterations()));
    }
}

