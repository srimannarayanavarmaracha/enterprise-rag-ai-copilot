package com.sriman.rag.rag;

import com.sriman.rag.domain.DocumentChunk;
import com.sriman.rag.repository.DocumentChunkRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Hybrid Retrieval Engine combining dense cosine similarity (Spring AI EmbeddingModel or
 * deterministic feature hashing fallback) with lexical BM25-style keyword overlap.
 */
@Service
public class HybridRetrievalService {

    private static final int LOCAL_DIM = 64;
    private static final double VECTOR_WEIGHT = 0.65;
    private static final double LEXICAL_WEIGHT = 0.35;

    private final DocumentChunkRepository chunkRepository;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public record ScoredChunk(
        Long chunkId,
        Long documentId,
        String documentTitle,
        int chunkIndex,
        String content,
        double hybridScore,
        double vectorScore,
        double lexicalScore
    ) {}

    public HybridRetrievalService(
        DocumentChunkRepository chunkRepository,
        ObjectProvider<EmbeddingModel> embeddingModelProvider
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingModelProvider = embeddingModelProvider;
    }

    public float[] embed(String text) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model != null) {
            try {
                return model.embed(text);
            } catch (Exception ignored) {
                // Fall back to deterministic feature-hashed vector if remote embedding call fails
            }
        }
        return localHashEmbedding(text);
    }

    public String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.5f", vector[i]));
        }
        return sb.toString();
    }

    public float[] deserializeVector(String csv) {
        String[] parts = csv.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i]);
        }
        return vec;
    }

    public List<ScoredChunk> retrieveTopK(String query, int topK) {
        List<DocumentChunk> allChunks = chunkRepository.findAllWithDocument();
        if (allChunks.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }

        float[] queryVec = embed(query);
        Set<String> queryTerms = tokenize(query);

        return allChunks.stream()
            .map(chunk -> {
                float[] chunkVec = deserializeVector(chunk.getEmbeddingCsv());
                double vecScore = cosineSimilarity(queryVec, chunkVec);
                double lexScore = lexicalOverlapScore(queryTerms, tokenize(chunk.getContent()));
                double hybrid = (VECTOR_WEIGHT * vecScore) + (LEXICAL_WEIGHT * lexScore);
                return new ScoredChunk(
                    chunk.getId(),
                    chunk.getDocument().getId(),
                    chunk.getDocument().getTitle(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    hybrid,
                    vecScore,
                    lexScore
                );
            })
            .sorted(Comparator.comparingDouble(ScoredChunk::hybridScore).reversed())
            .limit(topK)
            .toList();
    }

    private float[] localHashEmbedding(String text) {
        float[] vec = new float[LOCAL_DIM];
        for (String token : tokenize(text)) {
            int bucket = Math.floorMod(token.hashCode(), LOCAL_DIM);
            vec[bucket] += 1.0f;
            if (token.length() >= 4) {
                int bigramBucket = Math.floorMod(token.substring(0, 4).hashCode(), LOCAL_DIM);
                vec[bigramBucket] += 0.5f;
            }
        }
        double norm = 0.0;
        for (float v : vec) norm += v * v;
        double denom = Math.sqrt(norm);
        if (denom > 0) {
            for (int i = 0; i < vec.length; i++) {
                vec[i] = (float) (vec[i] / denom);
            }
        }
        return vec;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        if (len == 0) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : Math.max(0.0, dot / denom);
    }

    private double lexicalOverlapScore(Set<String> queryTerms, Set<String> docTerms) {
        if (queryTerms.isEmpty() || docTerms.isEmpty()) return 0.0;
        long matches = queryTerms.stream().filter(docTerms::contains).count();
        return (double) matches / queryTerms.size();
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(s -> s.length() > 1)
            .collect(Collectors.toSet());
    }
}
