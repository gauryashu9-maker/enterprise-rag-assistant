package com.axlero.ragassistant.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axlero.ragassistant.entities.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

    @Test
    void isAdminOnlyForAdminRole() {
        assertTrue(new AuthenticatedUser(UUID.randomUUID(), "a@x.com", Role.ADMIN).isAdmin());
        assertFalse(new AuthenticatedUser(UUID.randomUUID(), "u@x.com", Role.USER).isAdmin());
    }
}
