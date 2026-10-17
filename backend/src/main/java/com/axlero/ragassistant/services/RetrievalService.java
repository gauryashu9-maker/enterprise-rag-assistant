package com.axlero.ragassistant.services;

import java.util.List;
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
        @SuppressWarnings("null")
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        List<Document> results = vectorStore.similaritySearch(request);
        List<Document> chunks = results == null ? List.of() : results;
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
}   