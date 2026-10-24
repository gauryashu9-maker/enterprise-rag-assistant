package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatMessage;
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
import java.util.List;
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
        assertThat(json.has("filename")).isTrue();
        assertThat(json.get("filename").textValue()).isEqualTo("handbook.pdf");
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

    @Test
    void serializesChatSessionResponseAccordingToJsonContract() throws IOException {
        UUID id = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        ChatSessionResponse response = new ChatSessionResponse(
                id, userId, Instant.parse("2026-10-20T10:30:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.size()).isEqualTo(3);
        assertThat(json.get("id").textValue()).isEqualTo(id.toString());
        assertThat(json.get("userId").textValue()).isEqualTo(userId.toString());
        assertThat(json.get("createdAt").textValue()).isEqualTo("2026-10-20T10:30:00Z");
    }

    @Test
    void serializesChatMessageResponseAccordingToJsonContract() throws IOException {
        UUID id = UUID.fromString("33333333-3333-4333-8333-333333333333");
        UUID sessionId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        ChatMessageResponse response = new ChatMessageResponse(
                id,
                sessionId,
                ChatMessageRole.ASSISTANT,
                "The policy is documented.",
                Instant.parse("2026-10-20T10:31:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.size()).isEqualTo(5);
        assertThat(json.get("id").textValue()).isEqualTo(id.toString());
        assertThat(json.get("sessionId").textValue()).isEqualTo(sessionId.toString());
        assertThat(json.get("role").textValue()).isEqualTo("ASSISTANT");
        assertThat(json.get("content").textValue()).isEqualTo("The policy is documented.");
        assertThat(json.get("createdAt").textValue()).isEqualTo("2026-10-20T10:31:00Z");
    }

    @Test
    void serializesConversationWithSessionAndOrderedMessages() throws IOException {
        UUID sessionId = UUID.fromString("12345678-1234-5678-1234-567812345678");
        UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Instant sessionCreatedAt = Instant.parse("2026-10-20T10:00:00Z");
        ChatConversationResponse response = new ChatConversationResponse(
                new ChatSessionResponse(sessionId, userId, sessionCreatedAt),
                List.of(
                        new ChatMessageResponse(
                                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                                sessionId,
                                ChatMessageRole.USER,
                                "How many days off?",
                                Instant.parse("2026-10-20T10:00:01Z")),
                        new ChatMessageResponse(
                                UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                                sessionId,
                                ChatMessageRole.ASSISTANT,
                                "You get 24 days.",
                                Instant.parse("2026-10-20T10:00:02Z"))));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(response));

        assertThat(json.get("session").get("id").textValue()).isEqualTo(sessionId.toString());
        assertThat(json.get("session").get("userId").textValue()).isEqualTo(userId.toString());
        assertThat(json.get("session").get("createdAt").textValue()).isEqualTo("2026-10-20T10:00:00Z");
        assertThat(json.get("messages")).hasSize(2);
        assertThat(json.get("messages").get(0).get("id").textValue())
                .isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(json.get("messages").get(0).get("sessionId").textValue()).isEqualTo(sessionId.toString());
        assertThat(json.get("messages").get(0).get("role").textValue()).isEqualTo("USER");
        assertThat(json.get("messages").get(0).get("content").textValue()).isEqualTo("How many days off?");
        assertThat(json.get("messages").get(0).get("createdAt").textValue())
                .isEqualTo("2026-10-20T10:00:01Z");
        assertThat(json.get("messages").get(1).get("id").textValue())
                .isEqualTo("66666666-7777-8888-9999-aaaaaaaaaaaa");
        assertThat(json.get("messages").get(1).get("sessionId").textValue()).isEqualTo(sessionId.toString());
        assertThat(json.get("messages").get(1).get("role").textValue()).isEqualTo("ASSISTANT");
        assertThat(json.get("messages").get(1).get("content").textValue()).isEqualTo("You get 24 days.");
        assertThat(json.get("messages").get(1).get("createdAt").textValue())
                .isEqualTo("2026-10-20T10:00:02Z");
    }
}
