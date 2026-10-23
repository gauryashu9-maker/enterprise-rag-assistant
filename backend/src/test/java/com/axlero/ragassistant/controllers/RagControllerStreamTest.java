package com.axlero.ragassistant.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.dto.AskRequest;
import com.axlero.ragassistant.dto.RagStreamEvent;
import com.axlero.ragassistant.services.RagService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

class RagControllerStreamTest {

    @Test
    void mapsStreamEventsToServerSentEvents() {
        RagService ragService = mock(RagService.class);
        when(ragService.answerStream("q"))
                .thenReturn(Flux.just(RagStreamEvent.token("hi"), RagStreamEvent.done()));

        List<ServerSentEvent<Object>> events =
                new RagController(ragService).stream(new AskRequest("q")).collectList().block();

        assertEquals(2, events.size());
        assertEquals("token", events.get(0).event());
        assertEquals(Map.of("text", "hi"), events.get(0).data());
        assertEquals("done", events.get(1).event());
    }
}
