package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AskRequest;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.RagStreamEvent;
import com.axlero.ragassistant.services.RagService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
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
    public ResponseEntity<RagAnswer> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(ragService.answer(request.question()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody AskRequest request) {
        return ragService.answerStream(request.question())
                .map(e -> ServerSentEvent.<Object>builder().event(e.event()).data(e.data()).build());
    }
}
