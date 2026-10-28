package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

class RetrievalServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void usesConfiguredTopKAndThreshold() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("chunk")));

        List<Document> result = new RetrievalService(vectorStore, 3, 0.4, 0.85).retrieve("leave?", OWNER);

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

        assertTrue(new RetrievalService(vectorStore, 5, 0.4, 0.85).retrieve("anything", OWNER).isEmpty());
    }

    @Test
    void debugLogsDoNotContainFilenames() {
        Logger logger = (Logger) LoggerFactory.getLogger(RetrievalService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        try {
            VectorStore vectorStore = mock(VectorStore.class);
            Document chunk = Document.builder()
                    .text("chunk")
                    .metadata(Map.of("filename", "secret-salary.pdf", "page", 1))
                    .score(0.9)
                    .build();
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(chunk));

            new RetrievalService(vectorStore, 5, 0.55, 0.85).retrieve("q", OWNER);

            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("score=")));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("secret-salary.pdf")));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    @Test
    void keepNearBestDropsWeakChunksAndKeepsUnscored() {
        Document best = Document.builder().text("best").score(0.80).build();
        Document close = Document.builder().text("close").score(0.70).build();
        Document weak = Document.builder().text("weak").score(0.57).build();

        List<Document> kept = RetrievalService.keepNearBest(List.of(best, close, weak), 0.85);

        assertEquals(2, kept.size());
        assertEquals(1, RetrievalService.keepNearBest(List.of(new Document("no score")), 0.85).size());
    }

    @Test
    void retrieveDropsChunksFarBelowTheBestScore() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document best = Document.builder().text("best").score(0.80).build();
        Document weak = Document.builder().text("weak").score(0.57).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(best, weak));

        List<Document> result = new RetrievalService(vectorStore, 5, 0.55, 0.85).retrieve("q", OWNER);

        assertEquals(1, result.size());
        assertEquals("best", result.get(0).getText());
    }

    @Test
    void keepNearBestOnEmptyListReturnsEmpty() {
        assertTrue(RetrievalService.keepNearBest(List.of(), 0.85).isEmpty());
    }

    @Test
    void retrieveHonoursConfiguredRelativeCutoff() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document best = Document.builder().text("best").score(0.80).build();
        Document weak = Document.builder().text("weak").score(0.57).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(best, weak));

        assertEquals(2, new RetrievalService(vectorStore, 5, 0.55, 0.5).retrieve("q", OWNER).size());
        assertEquals(1, new RetrievalService(vectorStore, 5, 0.55, 0.85).retrieve("q", OWNER).size());
    }

    @Test
    void rejectsRelativeCutoffOutsideZeroToOne() {
        VectorStore vectorStore = mock(VectorStore.class);

        assertThrows(IllegalArgumentException.class, () -> new RetrievalService(vectorStore, 5, 0.55, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalService(vectorStore, 5, 0.55, -0.1));
    }

    @Test
    void searchIsFilteredToTheGivenOwnerOnly() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        RetrievalService service = new RetrievalService(vectorStore, 5, 0.55, 0.85);

        service.retrieve("q", OWNER);
        service.retrieve("q", other);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(2)).similaritySearch(captor.capture());
        assertEquals(new FilterExpressionBuilder().eq("ownerId", OWNER.toString()).build(),
                captor.getAllValues().get(0).getFilterExpression());
        assertEquals(new FilterExpressionBuilder().eq("ownerId", other.toString()).build(),
                captor.getAllValues().get(1).getFilterExpression());
    }

    @Test
    void retrievalWithoutAnOwnerIsRejected() {
        VectorStore vectorStore = mock(VectorStore.class);

        assertThrows(NullPointerException.class,
                () -> new RetrievalService(vectorStore, 5, 0.55, 0.85).retrieve("q", null));
    }
}
