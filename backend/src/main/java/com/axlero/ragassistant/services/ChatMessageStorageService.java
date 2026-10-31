package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.ChatMessageResponse;
import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ChatMessageStorageService {

    private final ChatSessionRepository chatSessionRepository;

    public ChatMessageStorageService(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = Objects.requireNonNull(chatSessionRepository, "ChatSessionRepository must not be null");
    }

    @Transactional(readOnly = true)
    public void validateOwnership(UUID sessionId, UUID userId) {
        Objects.requireNonNull(sessionId, "Session ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");
        if (chatSessionRepository.findByIdAndUserId(sessionId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Conversation not found");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PersistedMessagePair appendMessagePair(
            UUID sessionId, UUID userId, String userQuestion, String assistantAnswer) {
        Objects.requireNonNull(sessionId, "Session ID must not be null");
        Objects.requireNonNull(userId, "User ID must not be null");
        Objects.requireNonNull(userQuestion, "User question must not be null");
        Objects.requireNonNull(assistantAnswer, "Assistant answer must not be null");

        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        Instant now = Instant.now();
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER, userQuestion, now);
        ChatMessage assistantMessage = new ChatMessage(ChatMessageRole.ASSISTANT, assistantAnswer, now.plusMillis(1));

        session.addMessage(userMessage);
        session.addMessage(assistantMessage);

        chatSessionRepository.save(session);

        return new PersistedMessagePair(
                ChatMessageResponse.fromEntity(userMessage),
                ChatMessageResponse.fromEntity(assistantMessage));
    }

    public record PersistedMessagePair(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage) {
    }
}
