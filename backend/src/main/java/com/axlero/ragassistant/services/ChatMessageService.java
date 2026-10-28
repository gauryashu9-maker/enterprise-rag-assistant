package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatMessageResponse;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.SendChatMessageResponse;
import com.axlero.ragassistant.entities.ChatMessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);
    public static final int MAX_PERSISTENCE_ATTEMPTS = 3;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(50);

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final ChatMessageStorageService chatMessageStorageService;
    private final RagService ragService;
    private final Duration retryBackoff;
    private final Sleeper sleeper;

    public ChatMessageService(
            ChatMessageStorageService chatMessageStorageService,
            RagService ragService) {
        this(chatMessageStorageService, ragService, DEFAULT_BACKOFF, duration -> Thread.sleep(duration.toMillis()));
    }

    public ChatMessageService(
            ChatMessageStorageService chatMessageStorageService,
            RagService ragService,
            Duration retryBackoff,
            Sleeper sleeper) {
        this.chatMessageStorageService = chatMessageStorageService;
        this.ragService = ragService;
        this.retryBackoff = retryBackoff != null ? retryBackoff : DEFAULT_BACKOFF;
        this.sleeper = sleeper != null ? sleeper : duration -> Thread.sleep(duration.toMillis());
    }

    public SendChatMessageResponse sendMessage(
            UUID sessionId, String question, AuthenticatedUser user) {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(sessionId, "Session ID must not be null");
        Objects.requireNonNull(question, "Question must not be null");

        // 1. Verify session ownership (strict owner-only, including ADMIN; 404 for missing/cross-owner sessions)
        chatMessageStorageService.validateOwnership(sessionId, user.id());

        // 2. Call RAG outside database transaction so LLM calls never hold DB connections
        RagAnswer ragAnswer = ragService.answer(question, user.id());

        // 3. Persist USER and ASSISTANT messages atomically with up to 3 attempts for transient failures
        Optional<ChatMessageStorageService.PersistedMessagePair> persisted =
                persistMessagesWithRetry(sessionId, user.id(), question, ragAnswer.answer());

        if (persisted.isPresent()) {
            ChatMessageStorageService.PersistedMessagePair pair = persisted.get();
            return new SendChatMessageResponse(
                    pair.userMessage(),
                    pair.assistantMessage(),
                    ragAnswer.citations());
        }

        // 4. Best-effort fallback when persistence fails: return generated answer successfully with unpersisted message DTOs (null ID)
        Instant now = Instant.now();
        ChatMessageResponse unpersistedUserMessage = new ChatMessageResponse(
                null, sessionId, ChatMessageRole.USER, question, now);
        ChatMessageResponse unpersistedAssistantMessage = new ChatMessageResponse(
                null, sessionId, ChatMessageRole.ASSISTANT, ragAnswer.answer(), now.plusMillis(1));

        return new SendChatMessageResponse(
                unpersistedUserMessage,
                unpersistedAssistantMessage,
                ragAnswer.citations());
    }

    private Optional<ChatMessageStorageService.PersistedMessagePair> persistMessagesWithRetry(
            UUID sessionId, UUID userId, String question, String answer) {
        for (int attempt = 1; attempt <= MAX_PERSISTENCE_ATTEMPTS; attempt++) {
            try {
                return Optional.of(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer));
            } catch (TransientDataAccessException ex) {
                if (attempt < MAX_PERSISTENCE_ATTEMPTS) {
                    log.warn("Transient data access exception persisting chat messages for session ID: {} (attempt {}/{}); retrying",
                            sessionId, attempt, MAX_PERSISTENCE_ATTEMPTS, ex);
                    try {
                        sleeper.sleep(retryBackoff);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        log.error("Persistence retry backoff interrupted for session ID: {}", sessionId, interruptedException);
                        break;
                    }
                } else {
                    log.error("Exhausted retries persisting chat messages for session ID: {} after {} attempts",
                            sessionId, MAX_PERSISTENCE_ATTEMPTS, ex);
                }
            } catch (Exception ex) {
                // Non-transient failure: do not retry
                log.error("Non-transient persistence failure for session ID: {}", sessionId, ex);
                break;
            }
        }
        return Optional.empty();
    }
}
