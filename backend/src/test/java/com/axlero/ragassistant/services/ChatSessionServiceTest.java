package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatSessionResponse;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.repositories.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionServiceTest {

    private ChatSessionRepository chatSessionRepository;
    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        chatSessionRepository = mock(ChatSessionRepository.class);
        chatSessionService = new ChatSessionService(chatSessionRepository);
    }

    @Test
    void createsSessionForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();

        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            setId(session, sessionId);
            return session;
        });

        ChatSessionResponse response = chatSessionService.createSession(user);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(sessionId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.createdAt()).isNotNull();

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void listsSessionsForAuthenticatedUserInOrder() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);

        ChatSession session1 = new ChatSession(userId);
        UUID id1 = UUID.randomUUID();
        setId(session1, id1);

        ChatSession session2 = new ChatSession(userId);
        UUID id2 = UUID.randomUUID();
        setId(session2, id2);

        when(chatSessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(session1, session2));

        List<ChatSessionResponse> responses = chatSessionService.listSessions(user);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(id1);
        assertThat(responses.get(0).userId()).isEqualTo(userId);
        assertThat(responses.get(1).id()).isEqualTo(id2);
        assertThat(responses.get(1).userId()).isEqualTo(userId);

        verify(chatSessionRepository).findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void listsSessionsReturnsEmptyListWhenNoSessionsExist() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);

        when(chatSessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        List<ChatSessionResponse> responses = chatSessionService.listSessions(user);

        assertThat(responses).isEmpty();
        verify(chatSessionRepository).findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void rejectsNullUserOnCreateAndList() {
        assertThatThrownBy(() -> chatSessionService.createSession(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("User must not be null");

        assertThatThrownBy(() -> chatSessionService.listSessions(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("User must not be null");
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
