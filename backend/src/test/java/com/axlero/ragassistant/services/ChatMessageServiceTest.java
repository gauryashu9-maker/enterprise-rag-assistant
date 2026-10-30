package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatMessageResponse;
import com.axlero.ragassistant.dto.Citation;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.RagStreamEvent;
import com.axlero.ragassistant.dto.SendChatMessageResponse;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceTest {

    private ChatMessageStorageService chatMessageStorageService;
    private RagService ragService;
    private ChatMessageService.Sleeper mockSleeper;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageStorageService = mock(ChatMessageStorageService.class);
        ragService = mock(RagService.class);
        mockSleeper = mock(ChatMessageService.Sleeper.class);
        chatMessageService = new ChatMessageService(
                chatMessageStorageService, ragService, Duration.ZERO, mockSleeper);
    }

    @Test
    void sendsMessageSuccessfullyAndReturnsCombinedResponseWithCitations() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "How many vacation days do I get?";
        String answer = "You get 24 days.";
        List<Citation> citations = List.of(new Citation("handbook.pdf", 1, "24 days"));

        when(ragService.answer(question, userId)).thenReturn(new RagAnswer(answer, citations));

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
        verify(ragService).answer(question, userId);
        verify(chatMessageStorageService).appendMessagePair(sessionId, userId, question, answer);
    }

    @Test
    void verifiesRagBeforeSaveOrdering() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Order test question";
        String answer = "Order test answer";
        List<Citation> citations = List.of();

        when(ragService.answer(question, userId)).thenReturn(new RagAnswer(answer, citations));

        ChatMessageResponse userMsgResponse = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.USER, question, Instant.now());
        ChatMessageResponse assistantMsgResponse = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, answer, Instant.now().plusMillis(1));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenReturn(new ChatMessageStorageService.PersistedMessagePair(userMsgResponse, assistantMsgResponse));

        chatMessageService.sendMessage(sessionId, question, user);

        InOrder inOrder = inOrder(chatMessageStorageService, ragService);
        inOrder.verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        inOrder.verify(ragService).answer(question, userId);
        inOrder.verify(chatMessageStorageService).appendMessagePair(sessionId, userId, question, answer);
    }

    @Test
    void retriesTransientDataAccessExceptionAndSucceeds() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Retry question";
        String answer = "Retry answer";
        List<Citation> citations = List.of(new Citation("doc.pdf", 2, "snippet"));

        when(ragService.answer(question, userId)).thenReturn(new RagAnswer(answer, citations));

        ChatMessageResponse userMsgResponse = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.USER, question, Instant.now());
        ChatMessageResponse assistantMsgResponse = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, answer, Instant.now().plusMillis(1));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout"))
                .thenReturn(new ChatMessageStorageService.PersistedMessagePair(userMsgResponse, assistantMsgResponse));

        SendChatMessageResponse response = chatMessageService.sendMessage(sessionId, question, user);

        assertThat(response).isNotNull();
        assertThat(response.userMessage().id()).isEqualTo(userMsgResponse.id());
        assertThat(response.assistantMessage().id()).isEqualTo(assistantMsgResponse.id());
        assertThat(response.citations()).isEqualTo(citations);

        verify(chatMessageStorageService, times(2)).appendMessagePair(sessionId, userId, question, answer);
        verify(mockSleeper, times(1)).sleep(Duration.ZERO);
    }

    @Test
    void returnsGeneratedAnswerWithNullIdsWhenRetriesExhausted() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Exhausted retries question";
        String answer = "Exhausted retries answer";
        List<Citation> citations = List.of(new Citation("doc.pdf", 1, "snippet"));

        when(ragService.answer(question, userId)).thenReturn(new RagAnswer(answer, citations));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout"));

        SendChatMessageResponse response = chatMessageService.sendMessage(sessionId, question, user);

        assertThat(response).isNotNull();
        // Persistent IDs are null because persistence failed
        assertThat(response.userMessage().id()).isNull();
        assertThat(response.userMessage().sessionId()).isEqualTo(sessionId);
        assertThat(response.userMessage().role()).isEqualTo(ChatMessageRole.USER);
        assertThat(response.userMessage().content()).isEqualTo(question);

        assertThat(response.assistantMessage().id()).isNull();
        assertThat(response.assistantMessage().sessionId()).isEqualTo(sessionId);
        assertThat(response.assistantMessage().role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(response.assistantMessage().content()).isEqualTo(answer);

        assertThat(response.citations()).isEqualTo(citations);

        verify(chatMessageStorageService, times(3)).appendMessagePair(sessionId, userId, question, answer);
        verify(mockSleeper, times(2)).sleep(Duration.ZERO);
    }

    @Test
    void returnsGeneratedAnswerWithNullIdsImmediatelyOnNonTransientFailure() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Non transient question";
        String answer = "Non transient answer";
        List<Citation> citations = List.of(new Citation("doc.pdf", 1, "snippet"));

        when(ragService.answer(question, userId)).thenReturn(new RagAnswer(answer, citations));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenThrow(new DataIntegrityViolationException("Constraint violation"));

        SendChatMessageResponse response = chatMessageService.sendMessage(sessionId, question, user);

        assertThat(response).isNotNull();
        assertThat(response.userMessage().id()).isNull();
        assertThat(response.assistantMessage().id()).isNull();
        assertThat(response.assistantMessage().content()).isEqualTo(answer);
        assertThat(response.citations()).isEqualTo(citations);

        // Non-transient failure must NOT be retried
        verify(chatMessageStorageService, times(1)).appendMessagePair(sessionId, userId, question, answer);
        verify(mockSleeper, never()).sleep(any());
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

        verify(ragService, never()).answer(any(), any());
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

        verify(ragService, never()).answer(any(), any());
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void doesNotPersistMessagesWhenRagFails() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Complex prompt";

        when(ragService.answer(question, userId))
                .thenThrow(new IllegalStateException("RAG LLM connection timed out"));

        assertThatThrownBy(() -> chatMessageService.sendMessage(sessionId, question, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RAG LLM connection timed out");

        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(ragService).answer(question, userId);
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

    @Test
    void streamMessageEmitsEventsAndPersistsWhenDone() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "How many vacation days?";
        String answer = "You get 24 days.";
        List<Citation> citations = List.of(new Citation("handbook.pdf", 1, "24 days"));

        when(ragService.answerStream(question, userId)).thenReturn(Flux.just(
                RagStreamEvent.token("You get "),
                RagStreamEvent.token("24 days."),
                RagStreamEvent.citations(citations),
                RagStreamEvent.done()));

        ChatMessageResponse userMsg = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.USER, question, Instant.now());
        ChatMessageResponse assistantMsg = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, answer, Instant.now().plusMillis(1));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenReturn(new ChatMessageStorageService.PersistedMessagePair(userMsg, assistantMsg));

        List<RagStreamEvent> events = chatMessageService.streamMessage(sessionId, question, user)
                .collectList()
                .block();

        assertThat(events).hasSize(4);
        assertThat(events.stream().map(RagStreamEvent::event)).containsExactly("token", "token", "citations", "done");

        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(chatMessageStorageService).appendMessagePair(sessionId, userId, question, answer);
    }

    @Test
    void streamMessageFailsFastWithoutCallingRagWhenSessionNotOwned() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();

        doThrow(new ResourceNotFoundException("Conversation not found"))
                .when(chatMessageStorageService).validateOwnership(sessionId, userId);

        assertThatThrownBy(() -> chatMessageService.streamMessage(sessionId, "Hello", user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");

        verify(ragService, never()).answerStream(any(), any());
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void streamMessageDoesNotPersistOnStreamErrorEvent() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Failing question";

        when(ragService.answerStream(question, userId)).thenReturn(Flux.just(
                RagStreamEvent.token("part"),
                RagStreamEvent.error("RAG failed")));

        List<RagStreamEvent> events = chatMessageService.streamMessage(sessionId, question, user)
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void streamMessageDoesNotPersistOnReactiveOnError() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Error question";

        when(ragService.answerStream(question, userId))
                .thenReturn(Flux.error(new IllegalStateException("Reactive stream error")));

        Flux<RagStreamEvent> stream = chatMessageService.streamMessage(sessionId, question, user);

        assertThatThrownBy(() -> stream.collectList().block())
                .isInstanceOf(IllegalStateException.class);

        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void streamMessageDoesNotPersistWhenStreamEndsWithoutDone() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Incomplete question";

        when(ragService.answerStream(question, userId)).thenReturn(Flux.just(
                RagStreamEvent.token("partial response")));

        List<RagStreamEvent> events = chatMessageService.streamMessage(sessionId, question, user)
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void streamMessageDoesNotPersistOnClientCancellation() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Cancelled question";

        when(ragService.answerStream(question, userId)).thenReturn(
                Flux.concat(Flux.just(RagStreamEvent.token("part")), Flux.never()));

        List<RagStreamEvent> events = chatMessageService.streamMessage(sessionId, question, user)
                .take(1)
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(chatMessageStorageService, never()).appendMessagePair(any(), any(), any(), any());
    }

    @Test
    void streamMessagePersistenceFailureDoesNotBreakTheCompletedStream() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        String question = "Persistence failure question";
        String answer = "Valid answer text.";

        when(ragService.answerStream(question, userId)).thenReturn(Flux.just(
                RagStreamEvent.token(answer),
                RagStreamEvent.done()));

        when(chatMessageStorageService.appendMessagePair(sessionId, userId, question, answer))
                .thenThrow(new DataIntegrityViolationException("Database constraint violation"));

        List<RagStreamEvent> events = chatMessageService.streamMessage(sessionId, question, user)
                .collectList()
                .block();

        assertThat(events).hasSize(2);
        verify(chatMessageStorageService).validateOwnership(sessionId, userId);
        verify(chatMessageStorageService).appendMessagePair(sessionId, userId, question, answer);
    }

    @Test
    void streamMessageRejectsNullArguments() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();

        assertThatThrownBy(() -> chatMessageService.streamMessage(null, "q", user))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Session ID must not be null");

        assertThatThrownBy(() -> chatMessageService.streamMessage(sessionId, null, user))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Question must not be null");

        assertThatThrownBy(() -> chatMessageService.streamMessage(sessionId, "q", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("User must not be null");
    }
}
