
package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.DocumentRecord;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {

    private final DocumentService documentService;
    private final TextChunkingService textChunkingService;
    private final DocumentChunkStagingService stagingService;
    private final DocumentRecoveryAttemptService recoveryAttemptService;

    public DocumentIngestionService(
            DocumentService documentService,
            TextChunkingService textChunkingService,
            DocumentChunkStagingService stagingService,
            DocumentRecoveryAttemptService recoveryAttemptService) {
        this.documentService = documentService;
        this.textChunkingService = textChunkingService;
        this.stagingService = stagingService;
        this.recoveryAttemptService = recoveryAttemptService;
    }

    public DocumentRecord ingest(
            MultipartFile file, UUID ownerId) throws IOException {

        String text = documentService.extractText(file);
        List<String> chunks =
                textChunkingService.splitIntoChunks(text);

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "The uploaded file contains no text.");
        }

        String filename = file.getOriginalFilename() == null
                ? "document"
                : file.getOriginalFilename();

        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();

        DocumentRecord staged = stagingService.stageDocument(
                filename,
                contentType,
                file.getSize(),
                ownerId,
                chunks);

        RuntimeException failure =
                recoveryAttemptService.process(staged.getId());

        if (failure != null) {
            throw failure;
        }

        return staged;
    }
}
