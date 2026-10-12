package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axlero.ragassistant.dto.PingResponse;
import com.axlero.ragassistant.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class AiPingServiceTest {

    @Test
    void ping_returnsModelReply() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("pong");

        PingResponse response = new AiPingService(chatClient).ping("ping");

        assertEquals("pong", response.reply());
    }

    @Test
    void ping_mapsAiFailureToServiceUnavailable() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new IllegalStateException("AI unavailable"));

        assertThatThrownBy(() -> new AiPingService(chatClient).ping("ping"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
