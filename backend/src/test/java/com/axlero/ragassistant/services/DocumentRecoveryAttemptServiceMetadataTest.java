package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Owner-scoped search only works if every vector is written with the right
 * metadata. These tests check what DocumentRecoveryAttemptService puts on the
 * vectors it indexes.
 */
class DocumentRecoveryAttemptServiceMetadataTest {

    private DocumentRecordRepository documentRepository;
    private DocumentChunkStagingService stagingService;
    private VectorStore vectorStore;
    private DocumentRecoveryAttemptService recoveryService;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRecordRepository.class);
        stagingService = mock(DocumentChunkStagingService.class);
        vectorStore = mock(VectorStore.class);

        recoveryService = new DocumentRecoveryAttemptService(
                documentRepository, stagingService, vectorStore);
    }

    private void givenStagedDocument(
            UUID documentId, UUID ownerId, String filename, String... chunkTexts) {
        DocumentRecord record = new DocumentRecord(
                filename, "text/plain", 11L,
                DocumentStatus.PROCESSING, 0, ownerId);
        ReflectionTestUtils.setField(record, "id", documentId);
        when(documentRepository.findByIdForUpdate(documentId))
                .thenReturn(Optional.of(record));

        List<DocumentIngestionChunk> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < chunkTexts.length; i++) {
            chunks.add(new DocumentIngestionChunk(documentId, i, chunkTexts[i]));
        }
        when(stagingService.getStagedChunks(documentId)).thenReturn(chunks);
    }

    @Test
    @SuppressWarnings("unchecked")
    void writesDocumentOwnerAndFilenameMetadataOnEveryVector() {
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        givenStagedDocument(
                documentId, ownerId, "guide.txt", "first chunk", "second chunk");

        RuntimeException result = recoveryService.process(documentId);

        assertNull(result);

        ArgumentCaptor<List<Document>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> added = captor.getValue();
        assertEquals(2, added.size());
        assertEquals("first chunk", added.get(0).getText());
        assertEquals("second chunk", added.get(1).getText());

        for (Document vector : added) {
            assertEquals(
                    documentId.toString(),
                    vector.getMetadata().get(ChunkMetadata.DOCUMENT_ID));
            assertEquals(
                    ownerId.toString(),
                    vector.getMetadata().get(ChunkMetadata.OWNER_ID));
            assertEquals(
                    "guide.txt",
                    vector.getMetadata().get(ChunkMetadata.FILENAME));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void tagsVectorsOfDifferentUsersWithTheirOwnOwnerId() {
        UUID firstDocumentId = UUID.randomUUID();
        UUID firstOwnerId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        UUID secondOwnerId = UUID.randomUUID();
        givenStagedDocument(
                firstDocumentId, firstOwnerId, "first.txt", "alpha");
        givenStagedDocument(
                secondDocumentId, secondOwnerId, "second.txt", "beta");

        recoveryService.process(firstDocumentId);
        recoveryService.process(secondDocumentId);

        ArgumentCaptor<List<Document>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(captor.capture());

        List<List<Document>> calls = captor.getAllValues();
        Document firstVector = calls.get(0).get(0);
        Document secondVector = calls.get(1).get(0);

        assertEquals(
                firstOwnerId.toString(),
                firstVector.getMetadata().get(ChunkMetadata.OWNER_ID));
        assertEquals(
                firstDocumentId.toString(),
                firstVector.getMetadata().get(ChunkMetadata.DOCUMENT_ID));
        assertEquals(
                secondOwnerId.toString(),
                secondVector.getMetadata().get(ChunkMetadata.OWNER_ID));
        assertEquals(
                secondDocumentId.toString(),
                secondVector.getMetadata().get(ChunkMetadata.DOCUMENT_ID));
    }

    @Test
    void doesNothingWhenTheDocumentWasDeletedBeforeTheWorkerRuns() {
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByIdForUpdate(documentId))
                .thenReturn(Optional.empty());

        RuntimeException result = recoveryService.process(documentId);

        assertNull(result);
        // A deleted document must never get vectors written for it again.
        verifyNoInteractions(vectorStore, stagingService);
    }
}