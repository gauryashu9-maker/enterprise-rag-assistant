package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.Role;
import java.util.UUID;

public record RegisterResponse(UUID id, String email, Role role) {
}