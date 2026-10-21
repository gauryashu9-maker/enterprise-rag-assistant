package com.axlero.ragassistant.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void hashIsNotPlaintextAndMatchesOnlyTheRightPassword() {
        String hash = encoder.encode("secret123");

        assertNotEquals("secret123", hash);
        assertTrue(encoder.matches("secret123", hash));
        assertFalse(encoder.matches("wrong-password", hash));
    }

    @Test
    void sameInputGivesDifferentHashes() {
        assertNotEquals(encoder.encode("secret123"), encoder.encode("secret123"));
    }
}