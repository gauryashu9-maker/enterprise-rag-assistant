package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.ChatSession;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionResponse(UUID id, UUID userId, Instant createdAt) {

    public static ChatSessionResponse fromEntity(ChatSession session) {
        return new ChatSessionResponse(session.getId(), session.getUserId(), session.getCreatedAt());
    }
}
