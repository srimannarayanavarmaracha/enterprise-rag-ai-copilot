package com.sriman.rag.rag;

import com.sriman.rag.rag.HybridRetrievalService.ScoredChunk;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Generates grounded answers with inline citations [1], [2] via Spring AI ChatClient
 * when a live LLM provider (OpenAI / Google GenAI) is configured, with deterministic
 * grounded synthesis fallback when running offline or in test mode.
 */
@Service
public class LlmSynthesisService {

    private final ObjectProvider<ChatModel> chatModelProvider;

    public record RagAnswer(
        String answer,
        String provider,
        long retrievalLatencyMs,
        double topMatchScore,
        List<Citation> citations
    ) {}

    public record Citation(
        int index,
        Long documentId,
        String documentTitle,
        int chunkIndex,
        double score,
        String snippet
    ) {}

    public LlmSynthesisService(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    public RagAnswer synthesize(String question, List<ScoredChunk> retrieved, long retrievalLatencyMs) {
        List<Citation> citations = IntStream.range(0, retrieved.size())
            .mapToObj(i -> {
                ScoredChunk c = retrieved.get(i);
                String snippet = c.content().length() > 220
                    ? c.content().substring(0, 220) + "..."
                    : c.content();
                return new Citation(i + 1, c.documentId(), c.documentTitle(), c.chunkIndex(), c.hybridScore(), snippet);
            })
            .toList();

        double topScore = retrieved.isEmpty() ? 0.0 : retrieved.get(0).hybridScore();
        if (retrieved.isEmpty()) {
            return new RagAnswer(
                "No matching knowledge base chunks were found for your query. Please ingest a document first.",
                "local-grounded-fallback",
                retrievalLatencyMs,
                0.0,
                List.of()
            );
        }

        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            try {
                String contextBlock = IntStream.range(0, retrieved.size())
                    .mapToObj(i -> "[" + (i + 1) + "] (" + retrieved.get(i).documentTitle() + "): "
                        + retrieved.get(i).content())
                    .collect(Collectors.joining("\n\n"));

                String response = ChatClient.create(model)
                    .prompt()
                    .system("""
                        You are an Enterprise Technical Knowledge Copilot.
                        Answer ONLY using the provided context chunks. Cite sources inline using [1], [2], etc.
                        If the context does not contain the answer, state that clearly.
                        """)
                    .user("Context:\n" + contextBlock + "\n\nQuestion: " + question)
                    .call()
                    .content();

                return new RagAnswer(response, "spring-ai-llm", retrievalLatencyMs, topScore, citations);
            } catch (Exception ignored) {
                // Fall back gracefully if external LLM key is invalid or unreachable
            }
        }

        StringBuilder grounded = new StringBuilder();
        grounded.append("Based on the retrieved enterprise knowledge chunks:\n\n");
        for (int i = 0; i < retrieved.size(); i++) {
            ScoredChunk c = retrieved.get(i);
            grounded.append("- [").append(i + 1).append("] **")
                .append(c.documentTitle()).append("** (chunk #").append(c.chunkIndex()).append("): ")
                .append(c.content()).append("\n");
        }
        return new RagAnswer(
            grounded.toString().trim(),
            "local-grounded-fallback",
            retrievalLatencyMs,
            topScore,
            citations
        );
    }
}
