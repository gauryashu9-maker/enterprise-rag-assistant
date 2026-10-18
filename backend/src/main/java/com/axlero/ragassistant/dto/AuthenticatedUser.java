package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.Role;
import java.util.UUID;

/** The logged-in user, placed in the SecurityContext by the JWT filter. Controllers read it with @AuthenticationPrincipal. */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
