package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentQueryServiceTest {

    private final DocumentRecordRepository repository = mock(DocumentRecordRepository.class);
    private final DocumentQueryService service = new DocumentQueryService(repository);

    @Test
    void listsOnlyTheOwnersDocumentsInRepositoryOrder() {
        UUID ownerId = UUID.randomUUID();
        DocumentRecord newerDocument = new DocumentRecord(
                "handbook.pdf", "application/pdf", 2048, DocumentStatus.READY, 3, ownerId);
        DocumentRecord olderDocument = new DocumentRecord(
                "archive.pdf", "application/pdf", 1024, DocumentStatus.UPLOADED, 1, ownerId);
        when(repository.findAllByUploadedByOrderByUploadedAtDesc(ownerId))
                .thenReturn(List.of(newerDocument, olderDocument));

        List<DocumentResponse> result = service.listDocuments(ownerId);

        verify(repository).findAllByUploadedByOrderByUploadedAtDesc(ownerId);
        assertEquals(List.of("handbook.pdf", "archive.pdf"),
                result.stream().map(DocumentResponse::filename).toList());
        assertEquals("handbook.pdf", result.getFirst().filename());
        assertEquals(DocumentStatus.READY, result.getFirst().status());
    }

    @Test
    void looksUpDocumentWithinTheSpecifiedOwnerScope() {
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        DocumentRecord document = new DocumentRecord(
                "handbook.pdf", "application/pdf", 2048, DocumentStatus.READY, 3, ownerId);
        when(repository.findByIdAndUploadedBy(documentId, ownerId)).thenReturn(Optional.of(document));

        DocumentResponse result = service.getDocument(documentId, ownerId);

        verify(repository).findByIdAndUploadedBy(documentId, ownerId);
        assertEquals("handbook.pdf", result.filename());
    }

    @Test
    void returnsTheSameNotFoundErrorForMissingOrOtherOwnersDocument() {
        UUID missingDocumentId = UUID.randomUUID();
        UUID existingDocumentId = UUID.randomUUID();
        UUID requestedOwnerId = UUID.randomUUID();
        when(repository.findByIdAndUploadedBy(missingDocumentId, requestedOwnerId)).thenReturn(Optional.empty());
        when(repository.findByIdAndUploadedBy(existingDocumentId, requestedOwnerId)).thenReturn(Optional.empty());

        ResourceNotFoundException missingDocumentException = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getDocument(missingDocumentId, requestedOwnerId));
        ResourceNotFoundException otherOwnersDocumentException = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getDocument(existingDocumentId, requestedOwnerId));

        assertEquals("Document not found", missingDocumentException.getMessage());
        assertEquals(missingDocumentException.getMessage(), otherOwnersDocumentException.getMessage());
        verify(repository).findByIdAndUploadedBy(missingDocumentId, requestedOwnerId);
        verify(repository).findByIdAndUploadedBy(existingDocumentId, requestedOwnerId);
    }
}
