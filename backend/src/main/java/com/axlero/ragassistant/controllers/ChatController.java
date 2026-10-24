package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.dto.ChatSessionResponse;
import com.axlero.ragassistant.services.ChatConversationQueryService;
import com.axlero.ragassistant.services.ChatSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatConversationQueryService chatConversationQueryService;

    public ChatController(
            ChatSessionService chatSessionService,
            ChatConversationQueryService chatConversationQueryService) {
        this.chatSessionService = chatSessionService;
        this.chatConversationQueryService = chatConversationQueryService;
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
}
