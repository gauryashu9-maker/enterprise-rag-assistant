
package com.axlero.ragassistant.services;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TextChunkingService {

    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        String[] words = text.trim().split("\\s+");

        for (int i = 0; i < words.length; i += 500) {
            int end = Math.min(i + 500, words.length);
            chunks.add(String.join(" ",
                    java.util.Arrays.copyOfRange(words, i, end)));
        }

        return chunks;
    }
}
