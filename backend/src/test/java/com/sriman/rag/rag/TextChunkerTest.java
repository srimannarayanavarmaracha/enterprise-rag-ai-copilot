package com.sriman.rag.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void shouldSplitWithOverlapPreservingContext() {
        TextChunker chunker = new TextChunker();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 50; i++) {
            sb.append("word").append(i).append(" ");
        }

        List<String> chunks = chunker.chunk(sb.toString(), 20, 5);
        assertEquals(3, chunks.size());
        assertTrue(chunks.get(0).endsWith("word20"));
        assertTrue(chunks.get(1).startsWith("word16"));
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        TextChunker chunker = new TextChunker();
        assertTrue(chunker.chunk("   ", 20, 5).isEmpty());
    }
}
