package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentQueryService {

    private final DocumentRecordRepository documentRecordRepository;

    public DocumentQueryService(DocumentRecordRepository documentRecordRepository) {
        this.documentRecordRepository = documentRecordRepository;
    }

    public List<DocumentResponse> listDocuments(UUID ownerId) {
        return documentRecordRepository.findAllByUploadedByOrderByUploadedAtDesc(ownerId)
                .stream()
                .map(DocumentResponse::fromEntity)
                .toList();
    }

    public DocumentResponse getDocument(UUID documentId, UUID ownerId) {
        DocumentRecord document = documentRecordRepository.findByIdAndUploadedBy(documentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        return DocumentResponse.fromEntity(document);
    }
}
