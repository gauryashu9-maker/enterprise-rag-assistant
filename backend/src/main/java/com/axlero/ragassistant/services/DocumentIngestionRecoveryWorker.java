
package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestionRecoveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIngestionRecoveryWorker.class);

    private final DocumentRecordRepository documentRepository;
    private final DocumentRecoveryAttemptService attemptService;

    public DocumentIngestionRecoveryWorker(
            DocumentRecordRepository documentRepository,
            DocumentRecoveryAttemptService attemptService) {
        this.documentRepository = documentRepository;
        this.attemptService = attemptService;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.document-ingestion.recovery-delay-ms:15000}")
    public void recoverDueDocuments() {
        Instant now = Instant.now();

        List<DocumentRecord> due =
                documentRepository
                        .findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                                List.of(
                                        DocumentStatus.PROCESSING,
                                        DocumentStatus.FAILED),
                                now);

        due = new ArrayList<>(due);

        // Also recover PROCESSING documents left behind by a restart.
        due.addAll(
                documentRepository
                        .findTop20ByStatusAndNextRetryAtIsNullOrderByUploadedAtAsc(
                                DocumentStatus.PROCESSING));

        Map<UUID, DocumentRecord> unique = new LinkedHashMap<>();

        for (DocumentRecord record : due) {
            unique.put(record.getId(), record);
        }

        for (DocumentRecord record : unique.values()) {
            try {
                RuntimeException failure =
                        attemptService.process(record.getId());

                if (failure != null) {
                    log.warn(
                            "Recovery attempt failed for document {}: {}",
                            record.getId(),
                            failure.getMessage());
                }
            } catch (RuntimeException exception) {
                log.error(
                        "Recovery processing failed for document {}",
                        record.getId(),
                        exception);
            }
        }
    }
}
