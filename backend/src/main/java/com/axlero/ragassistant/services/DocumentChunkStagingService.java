
package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentIngestionChunkRepository;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentChunkStagingService {

    private final DocumentIngestionChunkRepository chunkRepository;
    private final DocumentRecordRepository documentRepository;

    public DocumentChunkStagingService(
            DocumentIngestionChunkRepository chunkRepository,
            DocumentRecordRepository documentRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DocumentRecord stageDocument(
            String filename,
            String contentType,
            long sizeBytes,
            UUID ownerId,
            List<String> chunks) {

        DocumentRecord record = new DocumentRecord(
                filename,
                contentType,
                sizeBytes,
                DocumentStatus.PROCESSING,
                0,
                ownerId);

        record.markProcessing(Instant.now());
        record = documentRepository.saveAndFlush(record);

        List<DocumentIngestionChunk> staged =
                new ArrayList<>(chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            staged.add(new DocumentIngestionChunk(
                    record.getId(), i, chunks.get(i)));
        }

        chunkRepository.saveAll(staged);
        chunkRepository.flush();

        return record;
    }

    @Transactional(readOnly = true)
    public List<DocumentIngestionChunk> getStagedChunks(UUID documentId) {
        return chunkRepository
                .findAllByDocumentIdOrderByChunkIndex(documentId);
    }

    @Transactional
    public void clearStagedChunks(UUID documentId) {
        chunkRepository.deleteAllByDocumentId(documentId);
    }
}

