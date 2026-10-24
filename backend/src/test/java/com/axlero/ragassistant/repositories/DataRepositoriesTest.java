package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.services.ChatConversationQueryService;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatConversationQueryService.class)
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

    @Autowired
    private ChatConversationQueryService conversationQueryService;

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

    @Test
    void listsChatSessionsByUserIdInNewestFirstOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        ChatSession session1 = new ChatSession(ownerId);
        ChatSession session2 = new ChatSession(ownerId);
        ChatSession otherSession = new ChatSession(otherOwnerId);

        chatSessions.saveAndFlush(session1);
        chatSessions.saveAndFlush(otherSession);
        chatSessions.saveAndFlush(session2);
        entityManager.clear();

        List<ChatSession> userSessions = chatSessions.findAllByUserIdOrderByCreatedAtDesc(ownerId);

        assertThat(userSessions)
                .extracting(ChatSession::getUserId)
                .containsOnly(ownerId);
        assertThat(userSessions)
                .extracting(ChatSession::getCreatedAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void retrievesConversationWithOrderedMessagesAndEnforcesOwnerScope() {
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        AuthenticatedUser otherUser = new AuthenticatedUser(otherOwnerId, "other@example.com", Role.USER);
        ChatSession ownedSession = new ChatSession(ownerId);
        Instant baseTime = Instant.parse("2026-10-23T10:00:00Z");
        ownedSession.addMessage(new ChatMessage(ChatMessageRole.USER, "First question", baseTime.plusSeconds(1)));
        ownedSession.addMessage(new ChatMessage(ChatMessageRole.ASSISTANT, "First answer", baseTime.plusSeconds(2)));
        ownedSession.addMessage(new ChatMessage(ChatMessageRole.USER, "Second question", baseTime.plusSeconds(3)));
        ChatSession savedSession = chatSessions.saveAndFlush(ownedSession);
        entityManager.clear();

        ChatConversationResponse response = conversationQueryService.getConversation(savedSession.getId(), owner);

        assertThat(response.session().id()).isEqualTo(savedSession.getId());
        assertThat(response.session().userId()).isEqualTo(ownerId);
        assertThat(response.messages())
                .extracting(message -> message.role().name() + ": " + message.content())
                .containsExactly(
                        "USER: First question",
                        "ASSISTANT: First answer",
                        "USER: Second question");
        assertThatThrownBy(() -> conversationQueryService.getConversation(savedSession.getId(), otherUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");
        assertThatThrownBy(() -> conversationQueryService.getConversation(UUID.randomUUID(), owner))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    void maintainsDeterministicChronologicalMessageOrderingRegardlessOfInsertionOrder() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        ChatSession session = new ChatSession(ownerId);
        Instant base = Instant.parse("2026-10-23T12:00:00Z");

        session.addMessage(new ChatMessage(ChatMessageRole.USER, "Third message", base.plusSeconds(30)));
        session.addMessage(new ChatMessage(ChatMessageRole.USER, "First message", base.plusSeconds(10)));
        session.addMessage(new ChatMessage(ChatMessageRole.ASSISTANT, "Second message", base.plusSeconds(20)));

        ChatSession savedSession = chatSessions.saveAndFlush(session);
        entityManager.clear();

        ChatConversationResponse response = conversationQueryService.getConversation(savedSession.getId(), owner);

        assertThat(response.messages())
                .extracting(message -> message.role().name() + ": " + message.content())
                .containsExactly(
                        "USER: First message",
                        "ASSISTANT: Second message",
                        "USER: Third message");
    }

    @Test
    void adminConversationAccessRemainsOwnerScoped() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN);
        ChatSession session = chatSessions.saveAndFlush(new ChatSession(ownerId));
        entityManager.clear();

        assertThatThrownBy(() -> conversationQueryService.getConversation(session.getId(), admin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Conversation not found");
    }

    @Test
    void retrievesEmptyConversationWithEmptyMessageList() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);
        ChatSession session = chatSessions.saveAndFlush(new ChatSession(ownerId));
        entityManager.clear();

        ChatConversationResponse response = conversationQueryService.getConversation(session.getId(), owner);

        assertThat(response.messages()).isEmpty();
    }

    @Test
    void entityGraphReducesConversationFetchFromTwoSqlStatementsToOne() {
        UUID ownerId = UUID.randomUUID();
        ChatSession session = new ChatSession(ownerId);
        Instant baseTime = Instant.parse("2026-10-23T10:00:00Z");
        session.addMessage(new ChatMessage(ChatMessageRole.USER, "Question", baseTime.plusSeconds(1)));
        session.addMessage(new ChatMessage(ChatMessageRole.ASSISTANT, "Answer", baseTime.plusSeconds(2)));
        ChatSession savedSession = chatSessions.saveAndFlush(session);
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        ChatSession baseline = entityManager.find(ChatSession.class, savedSession.getId());
        assertThat(Hibernate.isInitialized(baseline.getMessages())).isFalse();
        assertThat(baseline.getMessages()).hasSize(2);
        long baselineStatementCount = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();

        ChatSession optimized = chatSessions.findByIdAndUserId(savedSession.getId(), ownerId).orElseThrow();
        assertThat(Hibernate.isInitialized(optimized.getMessages())).isTrue();
        assertThat(optimized.getMessages()).hasSize(2);
        long optimizedStatementCount = statistics.getPrepareStatementCount();

        assertThat(baselineStatementCount).isEqualTo(2);
        assertThat(optimizedStatementCount).isEqualTo(1);
        System.out.printf(
                "Chat conversation SQL evidence: baseline=%d statements, entity-graph=%d statements%n",
                baselineStatementCount,
                optimizedStatementCount);
    }
}
