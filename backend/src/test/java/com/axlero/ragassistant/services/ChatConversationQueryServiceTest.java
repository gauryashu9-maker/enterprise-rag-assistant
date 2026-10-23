package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.ChatSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatConversationQueryServiceTest {

    private final ChatSessionRepository repository = mock(ChatSessionRepository.class);
    private final ChatConversationQueryService service = new ChatConversationQueryService(repository);

    @Test
    void returnsAnOwnedConversationWithOrderedMessages() {
        UUID ownerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        ChatSession session = new ChatSession(ownerId);
        session.addMessage(new ChatMessage(ChatMessageRole.USER, "Question"));
        session.addMessage(new ChatMessage(ChatMessageRole.ASSISTANT, "Answer"));
        when(repository.findByIdAndUserId(sessionId, ownerId)).thenReturn(Optional.of(session));

        ChatConversationResponse response = service.getConversation(sessionId, owner);

        verify(repository).findByIdAndUserId(sessionId, ownerId);
        assertThat(response.session().userId()).isEqualTo(ownerId);
        assertThat(response.messages()).extracting(message -> message.role().name())
                .containsExactly("USER", "ASSISTANT");
        assertThat(response.messages()).extracting(message -> message.content())
                .containsExactly("Question", "Answer");
    }

    @Test
    void returnsNotFoundWhenConversationDoesNotExist() {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        when(repository.findByIdAndUserId(sessionId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(sessionId, owner))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    void usesAuthenticatedOwnerAndCannotBeOverriddenByClientIdentity() {
        UUID sessionId = UUID.randomUUID();
        UUID authenticatedOwnerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(
                authenticatedOwnerId, "owner@example.com", Role.USER);
        when(repository.findByIdAndUserId(sessionId, authenticatedOwnerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(sessionId, owner))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository).findByIdAndUserId(sessionId, authenticatedOwnerId);
    }

    @Test
    void adminsRemainScopedToTheirOwnConversations() {
        UUID sessionId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN);
        when(repository.findByIdAndUserId(sessionId, adminId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(sessionId, admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");

        verify(repository).findByIdAndUserId(sessionId, adminId);
    }

    @Test
    void returnsAnEmptyConversationWhenThereAreNoMessages() {
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        when(repository.findByIdAndUserId(sessionId, ownerId))
                .thenReturn(Optional.of(new ChatSession(ownerId)));

        ChatConversationResponse response = service.getConversation(sessionId, owner);

        assertThat(response.messages()).isEmpty();
    }
}
