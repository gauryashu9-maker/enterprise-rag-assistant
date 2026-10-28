package com.axlero.ragassistant.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Finds the document chunks most similar to a question, among one user's documents only. */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;
    /** Keep only chunks scoring at least this fraction of the best chunk's score (0 to 1). */
    private final double relativeCutoff;

    public RetrievalService(VectorStore vectorStore,
                            @Value("${app.rag.top-k:5}") int topK,
                            @Value("${app.rag.similarity-threshold:0.55}") double similarityThreshold,
                            @Value("${app.rag.relative-cutoff:0.85}") double relativeCutoff) {
        if (relativeCutoff < 0.0 || relativeCutoff > 1.0) {
            throw new IllegalArgumentException("app.rag.relative-cutoff must be between 0 and 1, got " + relativeCutoff);
        }
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.relativeCutoff = relativeCutoff;
    }

    /** Searches only the chunks whose ownerId metadata equals {@code ownerId}. There is no unfiltered search. */
    public List<Document> retrieve(String question, UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId is required: retrieval is always scoped to one user");
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(new FilterExpressionBuilder().eq(ChunkMetadata.OWNER_ID, ownerId.toString()).build())
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        List<Document> chunks = keepNearBest(results == null ? List.of() : results, relativeCutoff);
        if (log.isDebugEnabled()) {
            for (Document chunk : chunks) {
                log.debug("retrieved chunkId={} page={} score={}",
                        chunk.getId(),
                        chunk.getMetadata().get("page"),
                        chunk.getScore());
            }
        }
        return chunks;
    }

    /** Keeps only chunks scoring within `ratio` of the best chunk. Chunks without a score are kept. */
    static List<Document> keepNearBest(List<Document> chunks, double ratio) {
        double best = chunks.stream()
                .map(Document::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        return chunks.stream()
                .filter(d -> d.getScore() == null || d.getScore() >= best * ratio)
                .toList();
    }
}
