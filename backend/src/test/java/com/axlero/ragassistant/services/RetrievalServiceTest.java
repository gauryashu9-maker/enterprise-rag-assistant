package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RetrievalServiceTest {

    @Test
    void usesConfiguredTopKAndThreshold() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("chunk")));

        List<Document> result = new RetrievalService(vectorStore, 3, 0.4).retrieve("leave?");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertEquals(1, result.size());
        assertEquals("leave?", captor.getValue().getQuery());
        assertEquals(3, captor.getValue().getTopK());
        assertEquals(0.4, captor.getValue().getSimilarityThreshold(), 0.0001);
    }

    @Test
    void returnsEmptyListWhenStoreReturnsNull() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(null);

        assertTrue(new RetrievalService(vectorStore, 5, 0.4).retrieve("anything").isEmpty());
    }
}