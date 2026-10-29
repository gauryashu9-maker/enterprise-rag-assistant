
package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRecoveryAttemptService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentRecoveryAttemptService.class);

    private static final int MAX_RETRIES = 5;

    private final DocumentRecordRepository documentRepository;
    private final DocumentChunkStagingService stagingService;
    private final VectorStore vectorStore;

    public DocumentRecoveryAttemptService(
            DocumentRecordRepository documentRepository,
            DocumentChunkStagingService stagingService,
            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.stagingService = stagingService;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public RuntimeException process(UUID documentId) {
        DocumentRecord record = documentRepository
                .findByIdForUpdate(documentId)
                .orElse(null);

        if (record == null || record.getStatus() == DocumentStatus.READY) {
            return null;
        }

        Instant now = Instant.now();

        if (record.getStatus() == DocumentStatus.FAILED
                && (record.getNextRetryAt() == null
                || record.getNextRetryAt().isAfter(now))) {
            return null;
        }

        if (record.getStatus() != DocumentStatus.PROCESSING
                && record.getStatus() != DocumentStatus.FAILED) {
            return null;
        }

        List<DocumentIngestionChunk> staged =
                stagingService.getStagedChunks(documentId);

        if (staged.isEmpty()) {
            IllegalStateException failure = new IllegalStateException(
                    "No staged chunks exist for document " + documentId);
            record.markPermanentlyFailed(failure.getMessage());
            documentRepository.save(record);
            return failure;
        }

        Filter.Expression filter = new FilterExpressionBuilder()
                .eq(ChunkMetadata.DOCUMENT_ID, documentId.toString())
                .build();

        try {
            // Remove partial vectors from an earlier attempt first.
            vectorStore.delete(filter);

            List<Document> documents = staged.stream()
                    .map(chunk -> new Document(
                            chunk.getContent(),
                            chunk.getContent(),
                            Map.of(
                                    ChunkMetadata.DOCUMENT_ID,
                                    documentId.toString(),
                                    ChunkMetadata.OWNER_ID,
                                    record.getUploadedBy().toString(),
                                    ChunkMetadata.FILENAME,
                                    record.getFilename())))
                    .toList();

            vectorStore.add(documents);

        } catch (RuntimeException indexingFailure) {
            try {
                vectorStore.delete(filter);
            } catch (RuntimeException cleanupFailure) {
                indexingFailure.addSuppressed(cleanupFailure);
                log.error(
                        "Vector cleanup failed for document {}",
                        documentId,
                        cleanupFailure);
            }

            int nextAttempt = record.getRetryCount() + 1;
            long delaySeconds = Math.min(
                    30L * (1L << Math.min(record.getRetryCount(), 6)),
                    3600L);

            Instant retryAt = nextAttempt >= MAX_RETRIES
                    ? null
                    : Instant.now().plusSeconds(delaySeconds);

            record.recordFailure(
                    "Document indexing failed: "
                            + safeMessage(indexingFailure),
                    retryAt,
                    MAX_RETRIES);

            documentRepository.save(record);
            return indexingFailure;
        }

        // READY only after vectorStore.add returns successfully.
        record.markReady(staged.size());
        documentRepository.save(record);
        stagingService.clearStagedChunks(documentId);

        return null;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message.length() > 500
                ? message.substring(0, 500)
                : message;
    }
}

