package com.axlero.ragassistant.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RagStreamEventTest {

    @Test
    void factoriesSetEventNamesAndJsonFriendlyPayloads() {
        assertEquals("token", RagStreamEvent.token("hi").event());
        assertEquals(Map.of("text", "hi"), RagStreamEvent.token("hi").data());
        assertEquals(Map.of("text", ""), RagStreamEvent.token(null).data());
        assertEquals("citations", RagStreamEvent.citations(List.of()).event());
        assertEquals(Map.of("citations", List.of()), RagStreamEvent.citations(List.of()).data());
        assertEquals("done", RagStreamEvent.done().event());
        assertEquals("error", RagStreamEvent.error("oops").event());
        assertEquals(Map.of("detail", "oops"), RagStreamEvent.error("oops").data());
    }
}
