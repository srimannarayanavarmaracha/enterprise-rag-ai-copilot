package com.sriman.rag.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Overlapping word-boundary text chunker preserving semantic context across chunk boundaries.
 */
@Component
public class TextChunker {

    public List<String> chunk(String text, int maxWords, int overlapWords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");
        if (words.length <= maxWords) {
            return List.of(normalized);
        }

        int step = Math.max(1, maxWords - overlapWords);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(words.length, start + maxWords);
            chunks.add(String.join(" ", java.util.Arrays.copyOfRange(words, start, end)));
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }
}
