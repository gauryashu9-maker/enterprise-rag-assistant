package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.exceptions.ServiceUnavailableException;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentDeletionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionService.class);

    private final DocumentRecordRepository documentRecordRepository;
    private final VectorStore vectorStore;

    public DocumentDeletionService(
            DocumentRecordRepository documentRecordRepository,
            VectorStore vectorStore) {
        this.documentRecordRepository = documentRecordRepository;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public void deleteDocument(UUID documentId, AuthenticatedUser user) {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(user, "user must not be null");

        // 1. Authorize first: lookup document based on role ownership
        DocumentRecord document;
        if (user.isAdmin()) {
            document = documentRecordRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        } else {
            document = documentRecordRepository.findByIdAndUploadedBy(documentId, user.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        }

        // 2. Delete vector chunks first using VectorStore delete-by-filter on documentId
        try {
            Filter.Expression filter = new FilterExpressionBuilder()
                    .eq("documentId", documentId.toString())
                    .build();
            vectorStore.delete(filter);
        } catch (Exception exception) {
            log.error("Failed to delete vector chunks for document {}", documentId, exception);
            throw new ServiceUnavailableException("Failed to delete vector chunks for document", exception);
        }

        // 3. Delete database document record only after successful vector deletion
        documentRecordRepository.delete(document);
        log.info("Successfully deleted document {} and its vector chunks", documentId);
    }
}
