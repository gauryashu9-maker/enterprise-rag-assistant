package com.axlero.ragassistant.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.services.ChunkMetadata;
import com.axlero.ragassistant.services.RetrievalService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Proves owner scoping against a real pgvector database. Only the query embedding is faked. */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OwnerScopedRetrievalIntegrationTest {

    private static final int DIMENSIONS = 1536;
    private static final int LEAVE_TOPIC = 0;
    private static final int BONUS_TOPIC = 1;

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
    private JdbcTemplate jdbcTemplate;

    private RetrievalService retrievalService;
    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();

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
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            return unitVector(query.contains("bonus") ? BONUS_TOPIC : LEAVE_TOPIC);
        });
        PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(DIMENSIONS)
                .build();
        retrievalService = new RetrievalService(vectorStore, 5, 0.55, 0.85);
    }

    @Test
    void returnsOnlyTheCallersChunksEvenWhenAnotherUsersChunkMatchesJustAsWell() {
        insertChunk(ownerA, "a-hr.pdf", "A leave policy", LEAVE_TOPIC);
        insertChunk(ownerB, "b-hr.pdf", "B leave policy", LEAVE_TOPIC);

        List<Document> forA = retrievalService.retrieve("leave question", ownerA);
        List<Document> forB = retrievalService.retrieve("leave question", ownerB);

        assertThat(forA).extracting(Document::getText).containsExactly("A leave policy");
        assertThat(forB).extracting(Document::getText).containsExactly("B leave policy");
    }

    @Test
    void aUserWithNoDocumentsGetsNothing() {
        insertChunk(ownerA, "a-hr.pdf", "A leave policy", LEAVE_TOPIC);

        assertThat(retrievalService.retrieve("leave question", UUID.randomUUID())).isEmpty();
    }

    @Test
    void chunksWithoutAnOwnerAreNeverReturned() {
        insertChunk(null, "legacy.pdf", "Ownerless chunk", LEAVE_TOPIC);
        insertChunk(ownerA, "a-hr.pdf", "A leave policy", LEAVE_TOPIC);

        assertThat(retrievalService.retrieve("leave question", ownerA))
                .extracting(Document::getText).containsExactly("A leave policy");
        assertThat(retrievalService.retrieve("leave question", ownerB)).isEmpty();
    }

    @Test
    void stillReturnsOnlyRelevantChunksWithinTheCallersDocuments() {
        insertChunk(ownerA, "a-hr.pdf", "A leave policy", LEAVE_TOPIC);
        insertChunk(ownerA, "a-pay.pdf", "A bonus policy", BONUS_TOPIC);

        assertThat(retrievalService.retrieve("leave question", ownerA))
                .extracting(Document::getText).containsExactly("A leave policy");
    }

    private void insertChunk(UUID owner, String filename, String text, int topic) {
        String ownerPart = owner == null ? "" : "\"" + ChunkMetadata.OWNER_ID + "\":\"" + owner + "\",";
        String metadata = "{" + ownerPart + "\"" + ChunkMetadata.FILENAME + "\":\"" + filename + "\",\""
                + ChunkMetadata.PAGE + "\":1}";
        jdbcTemplate.update(
                "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?::uuid, ?, ?::json, ?::vector)",
                UUID.randomUUID().toString(), text, metadata, vectorLiteral(topic));
    }

    private static float[] unitVector(int topic) {
        float[] vector = new float[DIMENSIONS];
        vector[topic] = 1.0f;
        return vector;
    }

    private static String vectorLiteral(int topic) {
        return IntStream.range(0, DIMENSIONS)
                .mapToObj(i -> i == topic ? "1" : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
