package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.hibernate.Hibernate;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataRepositoriesTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DocumentRecordRepository documentRecords;

    @Autowired
    private ChatSessionRepository chatSessions;

    @Autowired
    private ChatMessageRepository chatMessages;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesDocumentRecord() {
        DocumentRecord document = new DocumentRecord(
                "handbook.pdf", "application/pdf", 2048, DocumentStatus.READY, 3, UUID.randomUUID());

        DocumentRecord saved = documentRecords.saveAndFlush(document);
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        DocumentRecord reloaded = documentRecords.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(reloaded.getChunkCount()).isEqualTo(3);
    }

    @Test
    void findsOnlyOwnedDocumentsInNewestFirstOrder() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        DocumentRecord older = documentRecords.saveAndFlush(new DocumentRecord(
                "older.pdf", "application/pdf", 1024, DocumentStatus.UPLOADED, 0, owner));
        DocumentRecord newer = documentRecords.saveAndFlush(new DocumentRecord(
                "newer.pdf", "application/pdf", 2048, DocumentStatus.READY, 2, owner));
        DocumentRecord otherUsersDocument = documentRecords.saveAndFlush(new DocumentRecord(
                "private.pdf", "application/pdf", 512, DocumentStatus.UPLOADED, 0, otherOwner));

        List<DocumentRecord> ownedDocuments = documentRecords.findAllByUploadedByOrderByUploadedAtDesc(owner);

        assertThat(ownedDocuments)
                .extracting(DocumentRecord::getUploadedBy)
                .containsOnly(owner);
        assertThat(ownedDocuments)
                .extracting(DocumentRecord::getUploadedAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(documentRecords.findByIdAndUploadedBy(newer.getId(), owner))
                .contains(newer);
        assertThat(documentRecords.findByIdAndUploadedBy(otherUsersDocument.getId(), owner))
                .isEmpty();
        assertThat(documentRecords.findByIdAndUploadedBy(older.getId(), otherOwner))
                .isEmpty();
    }

    @Test
    void savesChatSessionAndMessage() {
        ChatSession session = new ChatSession(UUID.randomUUID());
        ChatMessage message = new ChatMessage(ChatMessageRole.USER, "What is the retention policy?");
        session.addMessage(message);

        ChatSession savedSession = chatSessions.saveAndFlush(session);
        entityManager.clear();

        assertThat(savedSession.getId()).isNotNull();
        ChatMessage reloadedMessage = chatMessages.findById(message.getId()).orElseThrow();
        assertThat(reloadedMessage.getRole()).isEqualTo(ChatMessageRole.USER);
        assertThat(Hibernate.isInitialized(reloadedMessage.getSession())).isFalse();
        assertThat(reloadedMessage.getSession().getId()).isEqualTo(savedSession.getId());
    }
}
