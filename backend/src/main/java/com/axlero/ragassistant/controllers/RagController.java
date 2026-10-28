package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AskRequest;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.services.RagService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ResponseEntity<RagAnswer> ask(@Valid @RequestBody AskRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ragService.answer(request.question(), user.id()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody AskRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ragService.answerStream(request.question(), user.id())
                .map(e -> ServerSentEvent.<Object>builder().event(e.event()).data(e.data()).build());
    }
}
