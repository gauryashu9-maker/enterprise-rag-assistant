package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Boundary tests for TextChunkingService. They pin down the current behaviour:
 * chunks of at most 500 words, no overlap, whitespace collapsed to single spaces.
 */
class TextChunkingServiceBoundaryTest {

    private final TextChunkingService service = new TextChunkingService();

    /** Builds "w0 w1 w2 ..." with the given number of unique words. */
    private static String words(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "w" + i)
                .collect(Collectors.joining(" "));
    }

    private static List<Integer> wordCountsOf(List<String> chunks) {
        return chunks.stream()
                .map(chunk -> chunk.split(" ").length)
                .toList();
    }

    // ---- empty and blank input -----------------------------------------------

    @Test
    void returnsNoChunksForEmptyString() {
        assertTrue(service.splitIntoChunks("").isEmpty());
    }

    @Test
    void returnsNoChunksForOnlyNewlinesAndTabs() {
        assertTrue(service.splitIntoChunks("\n\t \r\n").isEmpty());
    }

    // ---- chunk size boundaries -----------------------------------------------

    @Test
    void keepsASingleWordInOneChunk() {
        assertEquals(List.of("hello"), service.splitIntoChunks("hello"));
    }

    @Test
    void keepsExactly500WordsInOneChunk() {
        List<String> chunks = service.splitIntoChunks(words(500));

        assertEquals(List.of(500), wordCountsOf(chunks));
    }

    @Test
    void splitsExactly1000WordsIntoTwoFullChunks() {
        List<String> chunks = service.splitIntoChunks(words(1000));

        assertEquals(List.of(500, 500), wordCountsOf(chunks));
    }

    @Test
    void splits1001WordsIntoTwoFullChunksAndOneWord() {
        List<String> chunks = service.splitIntoChunks(words(1001));

        assertEquals(List.of(500, 500, 1), wordCountsOf(chunks));
    }

    // ---- whitespace and content ----------------------------------------------

    @Test
    void collapsesNewlinesAndTabsIntoSingleSpaces() {
        List<String> chunks = service.splitIntoChunks("hello\nworld\t\tagain");

        assertEquals(List.of("hello world again"), chunks);
    }

    @Test
    void keepsWordOrderAndLosesNoWords() {
        String original = words(1200);

        List<String> chunks = service.splitIntoChunks(original);

        assertEquals(List.of(500, 500, 200), wordCountsOf(chunks));
        // Joining the chunks again gives back the original text, so nothing is
        // dropped, duplicated or reordered (and there is no overlap).
        assertEquals(original, String.join(" ", chunks));
    }
}
