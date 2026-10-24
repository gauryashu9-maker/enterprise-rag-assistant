package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

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
    void listsOnlyAuthenticatedUsersDocuments() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(ownerId, "user@example.com", Role.USER);
        DocumentRecord document = new DocumentRecord(
                "handbook.pdf", "application/pdf", 2048, DocumentStatus.READY, 3, ownerId);
        when(repository.findAllByUploadedByOrderByUploadedAtDesc(ownerId))
                .thenReturn(List.of(document));

        List<DocumentResponse> result = service.listDocuments(user);

        verify(repository).findAllByUploadedByOrderByUploadedAtDesc(ownerId);
        assertEquals(List.of("handbook.pdf"), result.stream().map(DocumentResponse::filename).toList());
    }

    @Test
    void adminsCanListAllDocumentsInRepositoryOrder() {
        UUID adminId = UUID.randomUUID();
        UUID firstOwnerId = UUID.randomUUID();
        UUID secondOwnerId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN);
        DocumentRecord firstDocument = new DocumentRecord(
                "newer.pdf", "application/pdf", 2048, DocumentStatus.READY, 3, firstOwnerId);
        DocumentRecord secondDocument = new DocumentRecord(
                "older.pdf", "application/pdf", 1024, DocumentStatus.UPLOADED, 1, secondOwnerId);
        when(repository.findAllByOrderByUploadedAtDesc())
                .thenReturn(List.of(firstDocument, secondDocument));

        List<DocumentResponse> result = service.listDocuments(admin);

        verify(repository).findAllByOrderByUploadedAtDesc();
        assertEquals(List.of("newer.pdf", "older.pdf"),
                result.stream().map(DocumentResponse::filename).toList());
    }

    @Test
    void returnsAnEmptyListWhenTheOwnerHasNoDocuments() {
        UUID ownerId = UUID.randomUUID();
        when(repository.findAllByUploadedByOrderByUploadedAtDesc(ownerId)).thenReturn(List.of());

        List<DocumentResponse> result = service.listDocuments(ownerId);

        assertEquals(List.of(), result);
        verify(repository).findAllByUploadedByOrderByUploadedAtDesc(ownerId);
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
    void adminsCanGetDocumentsOwnedByOtherUsers() {
        UUID documentId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN);
        DocumentRecord document = new DocumentRecord(
                "private.pdf", "application/pdf", 512, DocumentStatus.UPLOADED, 0, ownerId);
        when(repository.findById(documentId)).thenReturn(Optional.of(document));

        DocumentResponse result = service.getDocument(documentId, admin);

        verify(repository).findById(documentId);
        assertEquals("private.pdf", result.filename());
    }

    @Test
    void returnsNotFoundWhenDocumentDoesNotExist() {
        UUID missingDocumentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(repository.findByIdAndUploadedBy(missingDocumentId, ownerId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getDocument(missingDocumentId, ownerId));

        assertEquals("Document not found", exception.getMessage());
        verify(repository).findByIdAndUploadedBy(missingDocumentId, ownerId);
    }

    @Test
    void returnsNotFoundWhenDocumentBelongsToAnotherOwner() {
        UUID documentId = UUID.randomUUID();
        UUID requestedOwnerId = UUID.randomUUID();
        UUID actualOwnerId = UUID.randomUUID();
        DocumentRecord otherOwnersDocument = new DocumentRecord(
                "private.pdf", "application/pdf", 512, DocumentStatus.UPLOADED, 0, actualOwnerId);
        when(repository.findByIdAndUploadedBy(
                ArgumentMatchers.any(UUID.class), ArgumentMatchers.any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID queriedOwnerId = invocation.getArgument(1, UUID.class);
                    return otherOwnersDocument.getUploadedBy().equals(queriedOwnerId)
                            ? Optional.of(otherOwnersDocument)
                            : Optional.empty();
                });

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getDocument(
                        documentId,
                        new AuthenticatedUser(requestedOwnerId, "user@example.com", Role.USER)));

        assertEquals("Document not found", exception.getMessage());
        verify(repository).findByIdAndUploadedBy(documentId, requestedOwnerId);
    }
}
