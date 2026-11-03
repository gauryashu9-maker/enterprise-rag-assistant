package com.axlero.ragassistant.services;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.repositories.DocumentRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unused")
@ExtendWith(MockitoExtension.class)
class DocumentRecoveryServiceTest {

    @Mock
    private DocumentRecordRepository documentRepository;
    @Mock
    private DocumentChunkStagingService stagingService;
    @Mock
    private VectorStore vectorStore;

    @InjectMocks
    private DocumentRecoveryAttemptService recoveryService;

    @SuppressWarnings("null")
    @Test
    void process_shouldGenerateValidUuidForChunks_NotUseTextContentAsId() {
        // Arrange
        UUID documentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String chunkText = "This is a test chunk content that is definitely too long to be a UUID.";

        DocumentRecord record = mock(DocumentRecord.class);
        when(record.getStatus()).thenReturn(DocumentStatus.PROCESSING);
        when(record.getUploadedBy()).thenReturn(ownerId);
        when(record.getFilename()).thenReturn("test.txt");

        DocumentIngestionChunk chunk = mock(DocumentIngestionChunk.class);
        when(chunk.getContent()).thenReturn(chunkText);

        when(documentRepository.findByIdForUpdate(documentId)).thenReturn(Optional.of(record));
        when(stagingService.getStagedChunks(documentId)).thenReturn(List.of(chunk));

        // Act
        recoveryService.process(documentId);

        // Assert
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> savedDocuments = captor.getValue();
        assertEquals(1, savedDocuments.size());
        
        Document savedDoc = savedDocuments.get(0);
        
        // The Regression Test: The ID must be a valid UUID, NOT the text content
        assertDoesNotThrow(() -> UUID.fromString(savedDoc.getId()), 
            "The document ID must be a valid UUID. Using text content causes 'UUID string too large'.");
        assertNotEquals(chunkText, savedDoc.getId(), 
            "The document ID should NOT be the text content.");
        assertEquals(chunkText, savedDoc.getText(), 
            "The text content should be stored properly in the document.");
    }
}