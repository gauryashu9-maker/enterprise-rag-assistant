package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ChatConversationQueryService {

    private final ChatSessionRepository chatSessionRepository;

    public ChatConversationQueryService(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    @Transactional(readOnly = true)
    public ChatConversationResponse getConversation(UUID sessionId, AuthenticatedUser user) {
        return chatSessionRepository.findByIdAndUserId(sessionId, user.id())
                .map(ChatConversationResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }
}
