package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.SendChatMessageResponse;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class ChatMessageService {

    private final ChatMessageStorageService chatMessageStorageService;
    private final RagService ragService;

    public ChatMessageService(
            ChatMessageStorageService chatMessageStorageService,
            RagService ragService) {
        this.chatMessageStorageService = chatMessageStorageService;
        this.ragService = ragService;
    }

    public SendChatMessageResponse sendMessage(
            UUID sessionId, String question, AuthenticatedUser user) {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(sessionId, "Session ID must not be null");
        Objects.requireNonNull(question, "Question must not be null");

        // 1. Verify session ownership (strict owner-only, including ADMIN)
        chatMessageStorageService.validateOwnership(sessionId, user.id());

        // 2. Call RagService.answer(question) outside database write transaction
        RagAnswer ragAnswer = ragService.answer(question);

        // 3. Atomically persist USER and ASSISTANT messages using separate transactional component
        ChatMessageStorageService.PersistedMessagePair pair = chatMessageStorageService.appendMessagePair(
                sessionId, user.id(), question, ragAnswer.answer());

        // 4. Return combined response preserving citations from RagAnswer
        return new SendChatMessageResponse(
                pair.userMessage(),
                pair.assistantMessage(),
                ragAnswer.citations());
    }
}
