package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID sessionId,
        ChatMessageRole role,
        String content,
        Instant createdAt) {

    public static ChatMessageResponse fromEntity(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(), message.getSession().getId(), message.getRole(),
                message.getContent(), message.getCreatedAt());
    }
}
