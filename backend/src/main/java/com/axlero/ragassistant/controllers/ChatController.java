package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.dto.ChatSessionResponse;
import com.axlero.ragassistant.dto.SendChatMessageRequest;
import com.axlero.ragassistant.dto.SendChatMessageResponse;
import com.axlero.ragassistant.services.ChatConversationQueryService;
import com.axlero.ragassistant.services.ChatMessageService;
import com.axlero.ragassistant.services.ChatSessionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatConversationQueryService chatConversationQueryService;
    private final ChatMessageService chatMessageService;

    public ChatController(
            ChatSessionService chatSessionService,
            ChatConversationQueryService chatConversationQueryService,
            @Nullable ChatMessageService chatMessageService) {
        this.chatSessionService = chatSessionService;
        this.chatConversationQueryService = chatConversationQueryService;
        this.chatMessageService = chatMessageService;
    }

    @PostMapping
    public ResponseEntity<ChatSessionResponse> createSession(
            @AuthenticationPrincipal AuthenticatedUser user,
            UriComponentsBuilder uriComponentsBuilder) {
        ChatSessionResponse session = chatSessionService.createSession(user);
        URI location = (uriComponentsBuilder != null ? uriComponentsBuilder : UriComponentsBuilder.newInstance())
                .path("/api/chat/sessions/{id}")
                .buildAndExpand(session.id())
                .toUri();
        return ResponseEntity.created(location).body(session);
    }

    @GetMapping
    public List<ChatSessionResponse> listSessions(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return chatSessionService.listSessions(user);
    }

    @GetMapping("/{id}")
    public ChatConversationResponse getConversation(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return chatConversationQueryService.getConversation(id, user);
    }

    @PostMapping("/{id}/messages")
    public SendChatMessageResponse sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (chatMessageService == null) {
            throw new IllegalStateException("ChatMessageService is not available");
        }
        return chatMessageService.sendMessage(id, request.message(), user);
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendChatMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (chatMessageService == null) {
            throw new IllegalStateException("ChatMessageService is not available");
        }
        return chatMessageService.streamMessage(id, request.message(), user)
                .map(e -> ServerSentEvent.<Object>builder().event(e.event()).data(e.data()).build());
    }
}
