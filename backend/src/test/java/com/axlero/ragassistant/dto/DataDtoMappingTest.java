package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DataDtoMappingTest {

    @Test
    void mapsDocumentRecordFields() {
        DocumentRecord document = new DocumentRecord(
                "handbook.pdf", "application/pdf", 2048, DocumentStatus.READY, 3, UUID.randomUUID());

        DocumentResponse response = DocumentResponse.fromEntity(document);

        assertThat(response.id()).isEqualTo(document.getId());
        assertThat(response.filename()).isEqualTo("handbook.pdf");
        assertThat(response.status()).isEqualTo(DocumentStatus.READY);
        assertThat(response.uploadedAt()).isEqualTo(document.getUploadedAt());
        assertThat(response.sizeBytes()).isEqualTo(2048L);
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void mapsStatusAndErrorMessageForFailedDocument() {
        DocumentRecord document = new DocumentRecord(
                "handbook.pdf", "application/pdf", 2048, DocumentStatus.PROCESSING, 0, UUID.randomUUID());
        document.updateStatus(DocumentStatus.FAILED, "Unsupported document format");

        DocumentResponse response = DocumentResponse.fromEntity(document);

        assertThat(response.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(response.errorMessage()).isEqualTo("Unsupported document format");
    }

    @Test
    void documentStatusValuesMatchTheApiContract() {
        assertThat(DocumentStatus.values()).containsExactly(
                DocumentStatus.UPLOADED, DocumentStatus.PROCESSING,
                DocumentStatus.READY, DocumentStatus.FAILED);
    }

    @Test
    void mapsChatSessionAndMessageFields() {
        ChatSession session = new ChatSession(UUID.randomUUID());
        ChatMessage message = new ChatMessage(ChatMessageRole.ASSISTANT, "The policy is documented.");
        session.addMessage(message);

        ChatSessionResponse sessionResponse = ChatSessionResponse.fromEntity(session);
        ChatMessageResponse messageResponse = ChatMessageResponse.fromEntity(message);

        assertThat(sessionResponse.userId()).isEqualTo(session.getUserId());
        assertThat(messageResponse.role()).isEqualTo(ChatMessageRole.ASSISTANT);
        assertThat(messageResponse.content()).isEqualTo("The policy is documented.");
        assertThat(messageResponse.sessionId()).isEqualTo(session.getId());
    }
}
