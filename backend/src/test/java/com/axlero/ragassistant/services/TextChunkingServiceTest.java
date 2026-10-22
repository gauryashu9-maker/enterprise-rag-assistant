
package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextChunkingServiceTest {

    private final TextChunkingService service = new TextChunkingService();

    @Test
    void returnsEmptyListForNullOrBlankText() {
        assertTrue(service.splitIntoChunks(null).isEmpty());
        assertTrue(service.splitIntoChunks("   ").isEmpty());
    }

    @Test
    void splitsTextIntoChunksOfAtMost500Words() {
        String text = String.join(" ",
                java.util.Collections.nCopies(501, "word"));

        List<String> chunks = service.splitIntoChunks(text);

        assertEquals(2, chunks.size());
        assertEquals(500, chunks.get(0).split("\\s+").length);
        assertEquals(1, chunks.get(1).split("\\s+").length);
    }

    @Test
    void handlesTextWithExtraWhitespace() {
        List<String> chunks = service.splitIntoChunks("  hello   world  ");

        assertEquals(1, chunks.size());
        assertEquals("hello world", chunks.get(0));
    }
}
