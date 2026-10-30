package com.axlero.ragassistant.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentIngestionRecoveryWorkerTest {

    private static final List<DocumentStatus> RECOVERABLE_STATUSES =
            List.of(DocumentStatus.PROCESSING, DocumentStatus.FAILED);

    private DocumentRecordRepository documentRepository;
    private DocumentRecoveryAttemptService attemptService;
    private DocumentIngestionRecoveryWorker worker;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRecordRepository.class);
        attemptService = mock(DocumentRecoveryAttemptService.class);

        worker = new DocumentIngestionRecoveryWorker(
                documentRepository, attemptService);
    }

    private DocumentRecord record(UUID id) {
        DocumentRecord record = new DocumentRecord(
                "guide.txt", "text/plain", 11L,
                DocumentStatus.PROCESSING, 0, UUID.randomUUID());
        ReflectionTestUtils.setField(record, "id", id);
        return record;
    }

    private void givenDueDocuments(
            List<DocumentRecord> dueForRetry,
            List<DocumentRecord> leftBehindByRestart) {
        when(documentRepository
                .findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        eq(RECOVERABLE_STATUSES), any(Instant.class)))
                .thenReturn(dueForRetry);
        when(documentRepository
                .findTop20ByStatusAndNextRetryAtIsNullOrderByUploadedAtAsc(
                        DocumentStatus.PROCESSING))
                .thenReturn(leftBehindByRestart);
    }

    @Test
    void doesNothingWhenNoDocumentsAreDue() {
        givenDueDocuments(List.of(), List.of());

        worker.recoverDueDocuments();

        verifyNoInteractions(attemptService);
    }

    @Test
    void processesDocumentsDueForRetryAndLeftBehindByRestart() {
        UUID dueId = UUID.randomUUID();
        UUID orphanedId = UUID.randomUUID();

        givenDueDocuments(
                List.of(record(dueId)),
                List.of(record(orphanedId)));

        worker.recoverDueDocuments();

        verify(attemptService).process(dueId);
        verify(attemptService).process(orphanedId);
    }

    @Test
    void processesDocumentOnlyOnceWhenItAppearsInBothQueries() {
        UUID documentId = UUID.randomUUID();

        givenDueDocuments(
                List.of(record(documentId)),
                List.of(record(documentId)));

        worker.recoverDueDocuments();

        verify(attemptService, times(1)).process(documentId);
    }

    @Test
    void keepsProcessingOtherDocumentsWhenAnAttemptReturnsFailure() {
        UUID failingId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();

        givenDueDocuments(
                List.of(record(failingId), record(healthyId)),
                List.of());

        when(attemptService.process(failingId))
                .thenReturn(new RuntimeException("vector index failed"));

        worker.recoverDueDocuments();

        verify(attemptService).process(failingId);
        verify(attemptService).process(healthyId);
    }

    @Test
    void keepsProcessingOtherDocumentsWhenAnAttemptThrows() {
        UUID throwingId = UUID.randomUUID();
        UUID healthyId = UUID.randomUUID();

        givenDueDocuments(
                List.of(record(throwingId), record(healthyId)),
                List.of());

        when(attemptService.process(throwingId))
                .thenThrow(new RuntimeException("unexpected error"));

        worker.recoverDueDocuments();

        verify(attemptService).process(throwingId);
        verify(attemptService).process(healthyId);
    }

    @Test
    void doesNotProcessDocumentsThatAreNotReturnedByTheQueries() {
        UUID dueId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        givenDueDocuments(List.of(record(dueId)), List.of());

        worker.recoverDueDocuments();

        verify(attemptService).process(dueId);
        verify(attemptService, never()).process(otherId);
    }
}
