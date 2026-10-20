package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.exceptions.ServiceUnavailableException;
import com.axlero.ragassistant.services.DocumentDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentDeleteIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DocumentRecordRepository documentRecords;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PgVectorStore vectorStore;
    private DocumentDeletionService deletionService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS vector_store (
                id uuid PRIMARY KEY,
                content text,
                metadata json,
                embedding vector(1536)
            )
        """);
        jdbcTemplate.execute("DELETE FROM vector_store");

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        this.vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1536)
                .build();
        this.deletionService = new DocumentDeletionService(documentRecords, vectorStore);
    }

    @Test
    void deletesDocumentAndOnlyItsAssociatedVectorChunksInPostgres() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);

        DocumentRecord doc1 = documentRecords.saveAndFlush(new DocumentRecord(
                "doc1.pdf", "application/pdf", 1024, DocumentStatus.READY, 2, ownerId));
        DocumentRecord doc2 = documentRecords.saveAndFlush(new DocumentRecord(
                "doc2.pdf", "application/pdf", 2048, DocumentStatus.READY, 1, ownerId));

        // Seed real document-specific chunks into PostgreSQL vector_store table
        UUID chunk1 = UUID.randomUUID();
        UUID chunk2 = UUID.randomUUID();
        UUID chunk3 = UUID.randomUUID();
        insertChunk(chunk1, "Doc1 page 1 text", doc1.getId(), 1);
        insertChunk(chunk2, "Doc1 page 2 text", doc1.getId(), 2);
        insertChunk(chunk3, "Doc2 page 1 text", doc2.getId(), 1);

        assertThat(countChunksForDocument(doc1.getId())).isEqualTo(2);
        assertThat(countChunksForDocument(doc2.getId())).isEqualTo(1);

        // Delete doc1
        deletionService.deleteDocument(doc1.getId(), owner);

        // Assert database record deleted for doc1, preserved for doc2
        assertThat(documentRecords.findById(doc1.getId())).isEmpty();
        assertThat(documentRecords.findById(doc2.getId())).isPresent();

        // Assert vector chunks deleted only for doc1, preserved for doc2
        assertThat(countChunksForDocument(doc1.getId())).isEqualTo(0);
        assertThat(countChunksForDocument(doc2.getId())).isEqualTo(1);
    }

    @Test
    void crossOwnerCannotDeleteDocumentOrChunksInPostgres() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        AuthenticatedUser otherUser = new AuthenticatedUser(otherUserId, "other@example.com", Role.USER);

        DocumentRecord doc = documentRecords.saveAndFlush(new DocumentRecord(
                "secret.pdf", "application/pdf", 512, DocumentStatus.READY, 1, ownerId));
        insertChunk(UUID.randomUUID(), "Secret content", doc.getId(), 1);

        assertThatThrownBy(() -> deletionService.deleteDocument(doc.getId(), otherUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Document not found");

        // Verify document and chunks preserved
        assertThat(documentRecords.findById(doc.getId())).isPresent();
        assertThat(countChunksForDocument(doc.getId())).isEqualTo(1);
    }

    @Test
    void adminCanDeleteDocumentAndAssociatedChunksAcrossOwners() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN);

        DocumentRecord doc = documentRecords.saveAndFlush(new DocumentRecord(
                "user-file.pdf", "application/pdf", 1024, DocumentStatus.READY, 1, ownerId));
        insertChunk(UUID.randomUUID(), "User file content", doc.getId(), 1);

        deletionService.deleteDocument(doc.getId(), admin);

        assertThat(documentRecords.findById(doc.getId())).isEmpty();
        assertThat(countChunksForDocument(doc.getId())).isEqualTo(0);
    }

    @Test
    void preservesDatabaseRecordWhenVectorStoreFails() {
        UUID ownerId = UUID.randomUUID();
        AuthenticatedUser owner = new AuthenticatedUser(ownerId, "owner@example.com", Role.USER);

        DocumentRecord doc = documentRecords.saveAndFlush(new DocumentRecord(
                "resilient.pdf", "application/pdf", 1024, DocumentStatus.READY, 1, ownerId));

        VectorStore failingVectorStore = mock(VectorStore.class);
        doThrow(new RuntimeException("Simulated vector store failure"))
                .when(failingVectorStore).delete(any(Filter.Expression.class));

        DocumentDeletionService resilientService = new DocumentDeletionService(documentRecords, failingVectorStore);

        assertThatThrownBy(() -> resilientService.deleteDocument(doc.getId(), owner))
                .isInstanceOf(ServiceUnavailableException.class);

        // Database document record must remain preserved
        assertThat(documentRecords.findById(doc.getId())).isPresent();
    }

    private void insertChunk(UUID chunkId, String content, UUID documentId, int page) {
        String metadataJson = String.format("{\"documentId\": \"%s\", \"page\": %d}", documentId, page);
        jdbcTemplate.update(
                "INSERT INTO vector_store (id, content, metadata) VALUES (?, ?, ?::json)",
                chunkId, content, metadataJson);
    }

    private int countChunksForDocument(UUID documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'documentId' = ?",
                Integer.class,
                documentId.toString());
        return count != null ? count : 0;
    }
}
