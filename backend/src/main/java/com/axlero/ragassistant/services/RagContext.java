package com.axlero.ragassistant.services;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.ai.document.Document;

/** Helpers for the structured answer flow: number the chunks for the prompt, then pick the cited ones. */
final class RagContext {

    private RagContext() {
    }

    /** Formats chunks as "[1] text", "[2] text", ... so the model can refer to them by number. */
    static String numbered(List<Document> chunks) {
        return IntStream.range(0, chunks.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + chunks.get(i).getText())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Picks the chunks the model said it used. Unknown or duplicate numbers are ignored.
     * If the model gave no valid number, all chunks are returned, because the answer was built from them.
     */
    static List<Document> selectCited(List<Integer> sources, List<Document> chunks) {
        if (sources == null || sources.isEmpty()) {
            return chunks;
        }
        List<Document> selected = sources.stream()
                .filter(Objects::nonNull)
                .filter(n -> n >= 1 && n <= chunks.size())
                .distinct()
                .sorted()
                .map(n -> chunks.get(n - 1))
                .toList();
        return selected.isEmpty() ? chunks : selected;
    }
}
