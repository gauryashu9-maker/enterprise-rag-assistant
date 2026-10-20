package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.ChatSession;

import java.util.List;

public record ChatConversationResponse(
        ChatSessionResponse session,
        List<ChatMessageResponse> messages) {

    public ChatConversationResponse {
        messages = List.copyOf(messages);
    }

    public static ChatConversationResponse fromEntity(ChatSession session) {
        return new ChatConversationResponse(
                ChatSessionResponse.fromEntity(session),
                session.getMessages().stream()
                        .map(ChatMessageResponse::fromEntity)
                        .toList());
    }
}
