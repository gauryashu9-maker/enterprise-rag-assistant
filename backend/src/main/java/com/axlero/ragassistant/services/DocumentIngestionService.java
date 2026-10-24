package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIngestionService {

    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentService documentService;
    private final TextChunkingService textChunkingService;
    private final VectorStore vectorStore;

    public DocumentIngestionService(
            DocumentRecordRepository documentRecordRepository,
            DocumentService documentService,
            TextChunkingService textChunkingService,
            VectorStore vectorStore) {
        this.documentRecordRepository = documentRecordRepository;
        this.documentService = documentService;
        this.textChunkingService = textChunkingService;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public DocumentRecord ingest(
            org.springframework.web.multipart.MultipartFile file,
            UUID ownerId) throws IOException {

        DocumentRecord record = documentRecordRepository.save(
                new DocumentRecord(
                        file.getOriginalFilename() == null
                                ? "document.txt"
                                : file.getOriginalFilename(),
                        file.getContentType() == null
                                ? "text/plain"
                                : file.getContentType(),
                        file.getSize(),
                        DocumentStatus.UPLOADED,
                        0,
                        ownerId));

        record.updateStatus(DocumentStatus.PROCESSING, null);
        documentRecordRepository.save(record);

        try {
            String text = documentService.extractText(file);
            List<String> chunks =
                    textChunkingService.splitIntoChunks(text);

            if (chunks.isEmpty()) {
                throw new IllegalArgumentException(
                        "The uploaded file contains no text.");
            }

            List<Document> documents = chunks.stream()
                    .map(chunk -> new Document(
                            chunk,
                            chunk,
                            Map.of(
                                    "documentId", record.getId().toString(),
                                    "ownerId", ownerId.toString(),
                                    "filename", record.getFilename())))
                    .toList();

            vectorStore.add(documents);

            record.updateChunkCount(documents.size());
            record.updateStatus(DocumentStatus.READY, null);

            return documentRecordRepository.save(record);

        } catch (Exception exception) {

            try {
                FilterExpressionBuilder builder =
                        new FilterExpressionBuilder();

                Filter.Expression filter = builder
                        .eq("documentId", record.getId().toString())
                        .build();

                vectorStore.delete(filter);

            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            record.updateStatus(
                    DocumentStatus.FAILED,
                    "Document ingestion failed.");

            documentRecordRepository.save(record);

            throw exception;
        }
    }
}