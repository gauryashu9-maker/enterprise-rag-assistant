package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentRecoveryAttemptServiceTest {

    private DocumentRecordRepository documentRepository;
    private DocumentChunkStagingService stagingService;
    private VectorStore vectorStore;
    private DocumentRecoveryAttemptService recoveryService;

    private UUID documentId;
    private DocumentRecord record;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRecordRepository.class);
        stagingService = mock(DocumentChunkStagingService.class);
        vectorStore = mock(VectorStore.class);

        recoveryService = new DocumentRecoveryAttemptService(
                documentRepository, stagingService, vectorStore);

        documentId = UUID.randomUUID();
        record = new DocumentRecord(
                "guide.txt", "text/plain", 11L,
                DocumentStatus.PROCESSING, 0, UUID.randomUUID());
        ReflectionTestUtils.setField(record, "id", documentId);

        when(documentRepository.findByIdForUpdate(documentId))
                .thenReturn(Optional.of(record));
    }

    private void givenOneStagedChunk() {
        when(stagingService.getStagedChunks(documentId)).thenReturn(
                List.of(new DocumentIngestionChunk(
                        documentId, 0, "hello world")));
    }

    @Test
    void marksRecordReadyWhenIndexingSucceeds() {
        givenOneStagedChunk();

        RuntimeException result = recoveryService.process(documentId);

        assertNull(result);
        assertEquals(DocumentStatus.READY, record.getStatus());
        assertEquals(1, record.getChunkCount());
        verify(vectorStore).add(anyList());
        verify(documentRepository).save(record);
        verify(stagingService).clearStagedChunks(documentId);
    }

    @Test
    void marksRecordFailedAndCleansUpWhenIndexingFails() {
        givenOneStagedChunk();
        doThrow(new RuntimeException("vector index failed"))
                .when(vectorStore).add(anyList());

        RuntimeException result = recoveryService.process(documentId);

        assertEquals("vector index failed", result.getMessage());
        assertEquals(DocumentStatus.FAILED, record.getStatus());
        // One delete before add (old partial vectors), one for cleanup.
        verify(vectorStore, times(2))
                .delete(any(Filter.Expression.class));
        verify(documentRepository).save(record);
        verify(stagingService, never()).clearStagedChunks(documentId);
    }

    @Test
    void preservesIndexingErrorWhenCleanupAlsoFails() {
        givenOneStagedChunk();
        doThrow(new RuntimeException("index failed"))
                .when(vectorStore).add(anyList());
        // First delete (before add) works, second one (cleanup) fails.
        doNothing()
                .doThrow(new RuntimeException("cleanup failed"))
                .when(vectorStore).delete(any(Filter.Expression.class));

        RuntimeException result = recoveryService.process(documentId);

        assertEquals("index failed", result.getMessage());
        assertEquals(1, result.getSuppressed().length);
        assertEquals(
                "cleanup failed", result.getSuppressed()[0].getMessage());
    }

    @Test
    void failsPermanentlyWhenNoStagedChunksExist() {
        when(stagingService.getStagedChunks(documentId))
                .thenReturn(List.of());

        RuntimeException result = recoveryService.process(documentId);

        assertInstanceOf(IllegalStateException.class, result);
        verify(documentRepository).save(record);
        verifyNoInteractions(vectorStore);
    }

    @Test
    void doesNothingWhenRecordIsAlreadyReady() {
        record.markReady(1);

        RuntimeException result = recoveryService.process(documentId);

        assertNull(result);
        verifyNoInteractions(vectorStore);
        verify(stagingService, never()).getStagedChunks(documentId);
    }
}
