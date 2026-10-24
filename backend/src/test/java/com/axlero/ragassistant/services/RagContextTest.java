package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RagContextTest {

    private final Document first = new Document("first chunk");
    private final Document second = new Document("second chunk");
    private final Document third = new Document("third chunk");
    private final List<Document> chunks = List.of(first, second, third);

    @Test
    void numbersChunksStartingAtOne() {
        assertEquals("[1] first chunk\n\n[2] second chunk\n\n[3] third chunk", RagContext.numbered(chunks));
    }

    @Test
    void selectsOnlyTheCitedChunksInOrderWithoutDuplicates() {
        assertEquals(List.of(first, third), RagContext.selectCited(List.of(3, 1, 3), chunks));
    }

    @Test
    void ignoresNumbersThatDoNotExist() {
        assertEquals(List.of(second), RagContext.selectCited(List.of(0, 2, 9), chunks));
    }

    @Test
    void fallsBackToAllChunksWhenNothingValidIsCited() {
        assertSame(chunks, RagContext.selectCited(List.of(9), chunks));
        assertSame(chunks, RagContext.selectCited(List.of(), chunks));
        assertSame(chunks, RagContext.selectCited(null, chunks));
    }
}
