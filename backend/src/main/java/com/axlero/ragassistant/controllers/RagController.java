package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AskRequest;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.services.RagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}