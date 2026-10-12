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

        DocumentRecordResponse response = DocumentRecordResponse.fromEntity(document);

        assertThat(response.filename()).isEqualTo("handbook.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.status()).isEqualTo(DocumentStatus.READY);
        assertThat(response.chunkCount()).isEqualTo(3);
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
