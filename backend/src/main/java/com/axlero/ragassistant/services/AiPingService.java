package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.PingResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Smoke-test service proving the app can reach the LLM through Spring AI's ChatClient. */
@Service
public class AiPingService {

    private final ChatClient chatClient;

    public AiPingService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PingResponse ping(String message) {
        String reply = chatClient.prompt().user(message).call().content();
        return new PingResponse(reply);
    }
}
