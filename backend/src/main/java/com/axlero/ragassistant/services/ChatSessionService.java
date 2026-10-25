package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatSessionResponse;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.repositories.ChatSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    public ChatSessionService(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    @Transactional
    public ChatSessionResponse createSession(AuthenticatedUser user) {
        Objects.requireNonNull(user, "User must not be null");
        ChatSession session = new ChatSession(user.id());
        ChatSession saved = chatSessionRepository.save(session);
        return ChatSessionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(AuthenticatedUser user) {
        Objects.requireNonNull(user, "User must not be null");
        return chatSessionRepository.findAllByUserIdOrderByCreatedAtDesc(user.id())
                .stream()
                .map(ChatSessionResponse::fromEntity)
                .toList();
    }
}
