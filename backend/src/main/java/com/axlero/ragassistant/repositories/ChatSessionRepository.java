package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.ChatSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    @EntityGraph(attributePaths = "messages")
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);
}
