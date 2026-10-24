package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageStorageServiceTest {

    private ChatSessionRepository chatSessionRepository;
    private ChatMessageStorageService storageService;

    @BeforeEach
    void setUp() {
        chatSessionRepository = mock(ChatSessionRepository.class);
        storageService = new ChatMessageStorageService(chatSessionRepository);
    }

    @Test
    void validateOwnershipSucceedsWhenSessionBelongsToUser() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatSession session = new ChatSession(userId);

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.of(session));

        storageService.validateOwnership(sessionId, userId);

        verify(chatSessionRepository).findByIdAndUserId(sessionId, userId);
    }

    @Test
    void validateOwnershipThrowsNotFoundWhenSessionMissingOrDifferentUser() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> storageService.validateOwnership(sessionId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    void appendMessagePairPersistsBothUserAndAssistantMessagesInOrder() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatSession session = new ChatSession(userId);
        setId(session, sessionId);

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageStorageService.PersistedMessagePair pair = storageService.appendMessagePair(
                sessionId, userId, "How many days off?", "You get 24 days.");

        assertThat(pair.userMessage().role()).isEqualTo(ChatMessageRole.USER);
        assertThat(pair.userMessage().content()).isEqualTo("How many days off?");
        assertThat(pair.assistantMessage().role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(pair.assistantMessage().content()).isEqualTo("You get 24 days.");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(captor.capture());
        ChatSession savedSession = captor.getValue();

        assertThat(savedSession.getMessages()).hasSize(2);
        ChatMessage userMsg = savedSession.getMessages().get(0);
        ChatMessage assistantMsg = savedSession.getMessages().get(1);

        assertThat(userMsg.getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(userMsg.getContent()).isEqualTo("How many days off?");
        assertThat(userMsg.getSession()).isEqualTo(savedSession);

        assertThat(assistantMsg.getRole()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(assistantMsg.getContent()).isEqualTo("You get 24 days.");
        assertThat(assistantMsg.getSession()).isEqualTo(savedSession);

        assertThat(userMsg.getCreatedAt()).isBeforeOrEqualTo(assistantMsg.getCreatedAt());
    }

    @Test
    void appendMessagePairThrowsNotFoundWhenSessionDoesNotExist() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> storageService.appendMessagePair(sessionId, userId, "Q", "A"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");
    }

    private void setId(ChatSession session, UUID id) {
        try {
            Field idField = ChatSession.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(session, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
