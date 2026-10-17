package com.axlero.ragassistant.services;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Finds the document chunks most similar to a question. */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    /** Keep only chunks scoring at least this fraction of the best chunk's score. */
    private static final double RELATIVE_CUTOFF = 0.85;

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public RetrievalService(VectorStore vectorStore,
                            @Value("${app.rag.top-k:5}") int topK,
                            @Value("${app.rag.similarity-threshold:0.55}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> retrieve(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        List<Document> chunks = keepNearBest(results == null ? List.of() : results, RELATIVE_CUTOFF);
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
