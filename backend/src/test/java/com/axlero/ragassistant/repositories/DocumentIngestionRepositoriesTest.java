package com.axlero.ragassistant.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the ingestion recovery queries and staged-chunk queries against a real
 * PostgreSQL database, so a wrong derived-query name or a broken migration is
 * caught here and not only in production.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentIngestionRepositoriesTest {

    private static final List<DocumentStatus> RECOVERABLE_STATUSES =
            List.of(DocumentStatus.PROCESSING, DocumentStatus.FAILED);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DocumentRecordRepository documentRecords;

    @Autowired
    private DocumentIngestionChunkRepository chunks;

    private DocumentRecord saveDocument(
            DocumentStatus status, Instant nextRetryAt) {
        DocumentRecord record = new DocumentRecord(
                "doc.txt", "text/plain", 100L, status, 0, UUID.randomUUID());
        ReflectionTestUtils.setField(record, "nextRetryAt", nextRetryAt);
        return documentRecords.saveAndFlush(record);
    }

    // ---- DocumentRecordRepository: retry queries ----------------------------

    @Test
    void findsOnlyProcessingAndFailedDocumentsThatAreDueOrderedByRetryTime() {
        Instant now = Instant.now();

        DocumentRecord failedDue =
                saveDocument(DocumentStatus.FAILED, now.minusSeconds(60));
        DocumentRecord processingDue =
                saveDocument(DocumentStatus.PROCESSING, now.minusSeconds(120));
        saveDocument(DocumentStatus.FAILED, now.plusSeconds(3600));
        saveDocument(DocumentStatus.READY, now.minusSeconds(60));

        List<DocumentRecord> due = documentRecords
                .findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        RECOVERABLE_STATUSES, now);

        assertThat(due)
                .extracting(DocumentRecord::getId)
                .containsExactly(processingDue.getId(), failedDue.getId());
    }

    @Test
    void findsOnlyProcessingDocumentsWithoutARetryTime() {
        DocumentRecord orphaned =
                saveDocument(DocumentStatus.PROCESSING, null);
        saveDocument(
                DocumentStatus.PROCESSING, Instant.now().plusSeconds(3600));
        saveDocument(DocumentStatus.FAILED, null);
        saveDocument(DocumentStatus.READY, null);

        List<DocumentRecord> left = documentRecords
                .findTop20ByStatusAndNextRetryAtIsNullOrderByUploadedAtAsc(
                        DocumentStatus.PROCESSING);

        assertThat(left)
                .extracting(DocumentRecord::getId)
                .containsExactly(orphaned.getId());
    }

    @Test
    void returnsAtMostTwentyDocumentsPerQuery() {
        for (int i = 0; i < 25; i++) {
            saveDocument(DocumentStatus.PROCESSING, null);
        }

        List<DocumentRecord> left = documentRecords
                .findTop20ByStatusAndNextRetryAtIsNullOrderByUploadedAtAsc(
                        DocumentStatus.PROCESSING);

        assertThat(left).hasSize(20);
    }

    // ---- DocumentRecordRepository: row lock ---------------------------------

    @Test
    void findByIdForUpdateReturnsTheDocument() {
        DocumentRecord saved = saveDocument(DocumentStatus.PROCESSING, null);

        Optional<DocumentRecord> found =
                documentRecords.findByIdForUpdate(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByIdForUpdateReturnsEmptyForUnknownId() {
        Optional<DocumentRecord> found =
                documentRecords.findByIdForUpdate(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    // ---- DocumentIngestionChunkRepository -----------------------------------

    @Test
    void returnsStagedChunksOrderedByChunkIndexForOneDocument() {
        UUID documentId =
                saveDocument(DocumentStatus.PROCESSING, null).getId();
        UUID otherDocumentId =
                saveDocument(DocumentStatus.PROCESSING, null).getId();

        chunks.saveAll(List.of(
                new DocumentIngestionChunk(documentId, 2, "third"),
                new DocumentIngestionChunk(documentId, 0, "first"),
                new DocumentIngestionChunk(documentId, 1, "second"),
                new DocumentIngestionChunk(otherDocumentId, 0, "other")));
        chunks.flush();

        List<DocumentIngestionChunk> result =
                chunks.findAllByDocumentIdOrderByChunkIndex(documentId);

        assertThat(result)
                .extracting(DocumentIngestionChunk::getContent)
                .containsExactly("first", "second", "third");
    }

    @Test
    void deletesStagedChunksOnlyForTheGivenDocument() {
        UUID documentId =
                saveDocument(DocumentStatus.PROCESSING, null).getId();
        UUID otherDocumentId =
                saveDocument(DocumentStatus.PROCESSING, null).getId();

        chunks.saveAll(List.of(
                new DocumentIngestionChunk(documentId, 0, "first"),
                new DocumentIngestionChunk(documentId, 1, "second"),
                new DocumentIngestionChunk(otherDocumentId, 0, "other")));
        chunks.flush();

        chunks.deleteAllByDocumentId(documentId);
        chunks.flush();

        assertThat(chunks.findAllByDocumentIdOrderByChunkIndex(documentId))
                .isEmpty();
        assertThat(chunks.findAllByDocumentIdOrderByChunkIndex(otherDocumentId))
                .extracting(DocumentIngestionChunk::getContent)
                .containsExactly("other");
    }
}
