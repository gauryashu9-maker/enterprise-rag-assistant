package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatMessageResponse;
import com.axlero.ragassistant.dto.Citation;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.SendChatMessageResponse;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceTest {

    private ChatMessageStorageService chatMessageStorageService;
    private RagService ragService;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageStorageService = mock(ChatMessageStorageService.class);
        ragService = mock(RagService.class);
        chatMessageService = new ChatMessageService(chatMessageStorageService, ragService);
    }

    @Test
    void sendsMessageSuccessfullyAndReturnsCombinedResponseWithCitations() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "How many vacation days do I get?";
        String answer = "You get 24 days.";
        List<Citation> citations = List.of(new Citation("handbook.pdf", 1, "24 days"));

        when(ragService.answer(question)).thenReturn(new RagAnswer(answer, citations));

        ChatMessageResponse userMsgResponse = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.USER, question, Instant.now());
        ChatMessageResponse assistantMsgResponse = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, answer, Instant.now().plusMillis(1));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenReturn(new ChatMessageStorageService.PersistedMessagePair(userMsgResponse, assistantMsgResponse));

        SendChatMessageResponse response = chatMessageService.sendMessage(sessionId, question, user);

        assertThat(response).isNotNull();
        assertThat(response.userMessage()).isEqualTo(userMsgResponse);
        assertThat(response.assistantMessage()).isEqualTo(assistantMsgResponse);
        assertThat(response.citations()).isEqualTo(citations);

        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(ragService).answer(question);
        verify(chatMessageStorageService).appendMessagePair(sessionId, userId, question, answer);
    }

    @Test
    void failsFastWithoutCallingRagWhenSessionNotOwned() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();

        doThrow(new ResourceNotFoundException("Conversation not found"))
                .when(chatMessageStorageService).validateOwnership(sessionId, userId);

        assertThatThrownBy(() -> chatMessageService.sendMessage(sessionId, "Hello", user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");

        verify(ragService, never()).answer(any());
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void failsFastWithoutCallingRagForAdminAccessToAnotherUsersSession() {
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN);
        UUID otherUserSessionId = UUID.randomUUID();

        doThrow(new ResourceNotFoundException("Conversation not found"))
                .when(chatMessageStorageService).validateOwnership(otherUserSessionId, adminId);

        assertThatThrownBy(() -> chatMessageService.sendMessage(otherUserSessionId, "Hello", admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");

        verify(ragService, never()).answer(any());
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void doesNotPersistMessagesWhenRagFails() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Complex prompt";

        when(ragService.answer(question))
                .thenThrow(new IllegalStateException("RAG LLM connection timed out"));

        assertThatThrownBy(() -> chatMessageService.sendMessage(sessionId, question, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RAG LLM connection timed out");

        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(ragService).answer(question);
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void rejectsNullArguments() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();

        assertThatThrownBy(() -> chatMessageService.sendMessage(null, "q", user))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Session ID must not be null");

        assertThatThrownBy(() -> chatMessageService.sendMessage(sessionId, null, user))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Question must not be null");

        assertThatThrownBy(() -> chatMessageService.sendMessage(sessionId, "q", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("User must not be null");
    }
}
