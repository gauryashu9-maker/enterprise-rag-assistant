package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.PingResponse;
import com.axlero.ragassistant.services.AiPingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiPingController {

    private final AiPingService aiPingService;

    public AiPingController(AiPingService aiPingService) {
        this.aiPingService = aiPingService;
    }

    @GetMapping("/ping")
    public ResponseEntity<PingResponse> ping(
            @RequestParam(defaultValue = "Reply with the single word: pong") String message) {
        return ResponseEntity.ok(aiPingService.ping(message));
    }
}
