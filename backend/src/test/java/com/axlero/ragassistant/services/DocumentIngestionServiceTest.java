package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentIngestionServiceTest {

    private DocumentService documentService;
    private TextChunkingService chunkingService;
    private DocumentChunkStagingService stagingService;
    private DocumentRecoveryAttemptService recoveryAttemptService;
    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        chunkingService = mock(TextChunkingService.class);
        stagingService = mock(DocumentChunkStagingService.class);
        recoveryAttemptService = mock(DocumentRecoveryAttemptService.class);

        ingestionService = new DocumentIngestionService(
                documentService,
                chunkingService,
                stagingService,
                recoveryAttemptService);
    }

    @Test
    void rejectsEmptyExtractedTextBeforeStagingAnything() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", " ".getBytes());

        when(documentService.extractText(file)).thenReturn(" ");
        when(chunkingService.splitIntoChunks(" ")).thenReturn(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> ingestionService.ingest(file, UUID.randomUUID()));

        verifyNoInteractions(stagingService, recoveryAttemptService);
    }

    @Test
    void returnsStagedRecordWhenRecoverySucceeds() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", "text/plain", "hello world".getBytes());

        DocumentRecord staged = new DocumentRecord(
                "guide.txt", "text/plain", 11L,
                DocumentStatus.PROCESSING, 0, ownerId);
        ReflectionTestUtils.setField(staged, "id", documentId);

        when(documentService.extractText(file)).thenReturn("hello world");
        when(chunkingService.splitIntoChunks("hello world"))
                .thenReturn(List.of("hello world"));
        when(stagingService.stageDocument(
                eq("guide.txt"),
                eq("text/plain"),
                eq(11L),
                eq(ownerId),
                eq(List.of("hello world"))))
                .thenReturn(staged);
        when(recoveryAttemptService.process(documentId)).thenReturn(null);

        DocumentRecord result = ingestionService.ingest(file, ownerId);

        assertSame(staged, result);
        verify(recoveryAttemptService).process(documentId);
    }

    @Test
    void throwsFailureReturnedByRecoveryAttempt() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", "text/plain", "hello world".getBytes());

        DocumentRecord staged = new DocumentRecord(
                "guide.txt", "text/plain", 11L,
                DocumentStatus.PROCESSING, 0, ownerId);
        ReflectionTestUtils.setField(staged, "id", documentId);

        RuntimeException failure = new RuntimeException("vector index failed");

        when(documentService.extractText(file)).thenReturn("hello world");
        when(chunkingService.splitIntoChunks("hello world"))
                .thenReturn(List.of("hello world"));
        when(stagingService.stageDocument(
                any(), any(), eq(11L), eq(ownerId), any()))
                .thenReturn(staged);
        when(recoveryAttemptService.process(documentId)).thenReturn(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> ingestionService.ingest(file, ownerId));

        assertEquals("vector index failed", thrown.getMessage());
    }
}