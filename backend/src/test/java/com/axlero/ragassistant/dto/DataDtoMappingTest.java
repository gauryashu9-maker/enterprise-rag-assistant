package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class DataDtoMappingTest {

    @Autowired
    private ObjectMapper objectMapper;

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
    void serializesDocumentResponseAccordingToJsonContract() throws IOException {
        UUID id = UUID.fromString("8ca57f15-4358-4bc6-a8af-614eab659cf4");
        Instant uploadedAt = Instant.parse("2026-10-15T12:30:00Z");
        DocumentResponse response = new DocumentResponse(
                id, "handbook.pdf", DocumentStatus.READY, uploadedAt, 2048L, null);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("id").textValue()).isEqualTo(id.toString());
        assertThat(json.get("status").textValue()).isEqualTo("READY");
        assertThat(json.get("uploadedAt").textValue()).isEqualTo("2026-10-15T12:30:00Z");
        assertThat(json.get("sizeBytes").isNumber()).isTrue();
        assertThat(json.get("sizeBytes").longValue()).isEqualTo(2048L);
        assertThat(json.has("errorMessage")).isFalse();
    }

    @Test
    void serializesFailedDocumentErrorMessage() throws IOException {
        DocumentResponse response = new DocumentResponse(
                UUID.randomUUID(),
                "handbook.pdf",
                DocumentStatus.FAILED,
                Instant.parse("2026-10-15T12:30:00Z"),
                2048L,
                "Unsupported document format");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.get("status").textValue()).isEqualTo("FAILED");
        assertThat(json.get("errorMessage").textValue()).isEqualTo("Unsupported document format");
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
