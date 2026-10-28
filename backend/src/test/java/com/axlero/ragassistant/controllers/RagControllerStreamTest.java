package com.axlero.ragassistant.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.dto.AskRequest;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.RagAnswer;
import com.axlero.ragassistant.dto.RagStreamEvent;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.services.RagService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

class RagControllerStreamTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(
            UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "user@example.com", Role.USER);

    @Test
    void mapsStreamEventsToServerSentEventsForTheAuthenticatedUser() {
        RagService ragService = mock(RagService.class);
        when(ragService.answerStream("q", USER.id()))
                .thenReturn(Flux.just(RagStreamEvent.token("hi"), RagStreamEvent.done()));

        List<ServerSentEvent<Object>> events =
                new RagController(ragService).stream(new AskRequest("q"), USER).collectList().block();

        assertEquals(2, events.size());
        assertEquals("token", events.get(0).event());
        assertEquals(Map.of("text", "hi"), events.get(0).data());
        assertEquals("done", events.get(1).event());
    }

    @Test
    void askUsesTheAuthenticatedUsersIdAndNeverARequestValue() {
        RagService ragService = mock(RagService.class);
        RagAnswer expected = new RagAnswer("24 days.", List.of());
        when(ragService.answer("q", USER.id())).thenReturn(expected);

        assertEquals(expected, new RagController(ragService).ask(new AskRequest("q"), USER).getBody());
    }
}
