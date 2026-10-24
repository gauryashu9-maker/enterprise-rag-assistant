package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.exceptions.ServiceUnavailableException;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentDeletionServiceTest {

    private final DocumentRecordRepository repository = mock(DocumentRecordRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentDeletionService service = new DocumentDeletionService(repository, vectorStore);

    @Test
    void ownerCanDeleteDocumentAndItsVectorsInOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        DocumentRecord document = new DocumentRecord(
                "policy.pdf", "application/pdf", 1024, DocumentStatus.READY, 2, ownerId);
        when(repository.findByIdAndUploadedBy(documentId, ownerId)).thenReturn(Optional.of(document));

        service.deleteDocument(documentId, owner);

        InOrder inOrder = inOrder(vectorStore, repository);
        inOrder.verify(vectorStore).delete(any(Filter.Expression.class));
        inOrder.verify(repository).delete(document);
    }

    @Test
    void adminCanDeleteDocumentOwnedByAnotherUser() {
        UUID adminId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN);
        DocumentRecord document = new DocumentRecord(
                "user-doc.pdf", "application/pdf", 2048, DocumentStatus.READY, 5, ownerId);
        when(repository.findById(documentId)).thenReturn(Optional.of(document));

        service.deleteDocument(documentId, admin);

        verify(repository).findById(documentId);
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(repository).delete(document);
    }

    @Test
    void crossOwnerUserReceivesNotFoundWithoutTouchingVectorStore() {
        UUID requestedOwnerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(requestedOwnerId, "user@example.com", Role.USER);
        when(repository.findByIdAndUploadedBy(documentId, requestedOwnerId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.deleteDocument(documentId, user));

        assertEquals("Document not found", exception.getMessage());
        verify(repository).findByIdAndUploadedBy(documentId, requestedOwnerId);
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(repository, never()).delete(any(DocumentRecord.class));
    }

    @Test
    void missingDocumentThrowsNotFoundWithoutTouchingVectorStore() {
        UUID missingId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN);
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.deleteDocument(missingId, admin));

        assertEquals("Document not found", exception.getMessage());
        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(repository, never()).delete(any(DocumentRecord.class));
    }

    @Test
    void preservesDatabaseRecordAndThrowsServiceUnavailableWhenVectorStoreFails() {
        UUID ownerId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        DocumentRecord document = new DocumentRecord(
                "important.pdf", "application/pdf", 1024, DocumentStatus.READY, 2, ownerId);
        when(repository.findByIdAndUploadedBy(documentId, ownerId)).thenReturn(Optional.of(document));
        doThrow(new RuntimeException("Vector database timeout"))
                .when(vectorStore).delete(any(Filter.Expression.class));

        ServiceUnavailableException exception = assertThrows(
                ServiceUnavailableException.class,
                () -> service.deleteDocument(documentId, owner));

        assertEquals("Failed to delete vector chunks for document", exception.getMessage());
        // Verify database document record was NOT deleted
        verify(repository, never()).delete(any(DocumentRecord.class));
    }
}
