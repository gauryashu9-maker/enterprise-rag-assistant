package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.ChatMessage;
import com.axlero.ragassistant.entities.ChatSession;
import com.axlero.ragassistant.entities.DocumentRecord;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LegacyDocumentMigrationTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SESSION_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesLegacyDocumentsWithoutChangingExistingDataAndValidatesJpaSchema() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        createLegacySchemaAndData(dataSource);
        List<DocumentSnapshot> beforeMigration = readDocuments(dataSource);

        Flyway flyway = flywayUsingApplicationBaselineSettings(dataSource);
        flyway.migrate();

        assertThat(readFlywayHistory(dataSource)).containsExactly(
                new FlywayHistory("0", "BASELINE", "<< Flyway Baseline >>", true),
                new FlywayHistory("1", "SQL",
                        "V1__create_data_schema_and_migrate_document_status.sql", true));

        List<DocumentSnapshot> expectedAfterMigration = beforeMigration.stream()
                .map(DocumentSnapshot::afterStatusMigration)
                .toList();
        assertThat(readDocuments(dataSource)).containsExactlyElementsOf(expectedAfterMigration);
        assertThat(readChatRecordCounts(dataSource)).containsExactly(1, 1);
        assertThat(errorMessageColumnIsNullableText(dataSource)).isTrue();

        validateJpaSchema(dataSource);
    }

    private static Flyway flywayUsingApplicationBaselineSettings(DataSource dataSource) throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        PropertySource<?> applicationProperties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();
        environment.getPropertySources().addFirst(applicationProperties);

        boolean baselineOnMigrate = environment.getRequiredProperty(
                "spring.flyway.baseline-on-migrate", Boolean.class);
        MigrationVersion baselineVersion = MigrationVersion.fromVersion(
                environment.getRequiredProperty("spring.flyway.baseline-version"));
        assertThat(baselineOnMigrate).isTrue();
        assertThat(baselineVersion.getVersion()).isEqualTo("0");

        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("public")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load();
    }

    private static void createLegacySchemaAndData(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE chat_sessions (
                            id uuid PRIMARY KEY,
                            user_id uuid NOT NULL,
                            created_at timestamp(6) with time zone NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE chat_messages (
                            id uuid PRIMARY KEY,
                            role varchar(255) NOT NULL,
                            content text NOT NULL,
                            created_at timestamp(6) with time zone NOT NULL,
                            session_id uuid NOT NULL REFERENCES chat_sessions (id)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE document_records (
                            id uuid PRIMARY KEY,
                            filename varchar(255) NOT NULL,
                            content_type varchar(255) NOT NULL,
                            size_bytes bigint NOT NULL,
                            status varchar(255) NOT NULL,
                            chunk_count integer NOT NULL,
                            uploaded_by uuid NOT NULL,
                            uploaded_at timestamp(6) with time zone NOT NULL
                        )
                        """);
            }

            try (PreparedStatement session = connection.prepareStatement("""
                    INSERT INTO chat_sessions (id, user_id, created_at) VALUES (?, ?, ?)
                    """)) {
                session.setObject(1, SESSION_ID);
                session.setObject(2, SESSION_USER_ID);
                session.setObject(3, OffsetDateTime.ofInstant(
                        Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC));
                session.executeUpdate();
            }

            try (PreparedStatement message = connection.prepareStatement("""
                    INSERT INTO chat_messages (id, role, content, created_at, session_id)
                    VALUES (?, 'USER', ?, ?, ?)
                    """)) {
                message.setObject(1, MESSAGE_ID);
                message.setString(2, "Existing chat data");
                message.setObject(3, OffsetDateTime.ofInstant(
                        Instant.parse("2026-01-02T03:05:06Z"), ZoneOffset.UTC));
                message.setObject(4, SESSION_ID);
                message.executeUpdate();
            }

            try (PreparedStatement document = connection.prepareStatement("""
                    INSERT INTO document_records
                        (id, filename, content_type, size_bytes, status, chunk_count, uploaded_by, uploaded_at)
                    VALUES (?, ?, 'application/pdf', ?, ?, ?, ?, ?)
                    """)) {
                insertDocument(document, "00000000-0000-0000-0000-000000000301",
                        "uploading.pdf", 101L, "UPLOADING", 0, "2026-02-03T04:05:06Z");
                insertDocument(document, "00000000-0000-0000-0000-000000000302",
                        "processing.pdf", 202L, "PROCESSING", 1, "2026-02-04T05:06:07Z");
                insertDocument(document, "00000000-0000-0000-0000-000000000303",
                        "ready.pdf", 303L, "READY", 2, "2026-02-05T06:07:08Z");
                insertDocument(document, "00000000-0000-0000-0000-000000000304",
                        "failed.pdf", 404L, "FAILED", 3, "2026-02-06T07:08:09Z");
            }
        }
    }

    private static void insertDocument(PreparedStatement statement, String id, String filename,
                                       long sizeBytes, String status, int chunkCount,
                                       String uploadedAt) throws SQLException {
        statement.setObject(1, UUID.fromString(id));
        statement.setString(2, filename);
        statement.setLong(3, sizeBytes);
        statement.setString(4, status);
        statement.setInt(5, chunkCount);
        statement.setObject(6, OWNER_ID);
        statement.setObject(7, OffsetDateTime.ofInstant(
                Instant.parse(uploadedAt), ZoneOffset.UTC));
        statement.executeUpdate();
    }

    private static List<DocumentSnapshot> readDocuments(DataSource dataSource) throws SQLException {
        List<DocumentSnapshot> documents = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rows = connection.createStatement().executeQuery("""
                     SELECT id, filename, content_type, size_bytes, status, chunk_count,
                            uploaded_by, uploaded_at
                     FROM document_records
                     ORDER BY id
                     """)) {
            while (rows.next()) {
                documents.add(new DocumentSnapshot(
                        rows.getObject("id", UUID.class),
                        rows.getString("filename"),
                        rows.getString("content_type"),
                        rows.getLong("size_bytes"),
                        rows.getString("status"),
                        rows.getInt("chunk_count"),
                        rows.getObject("uploaded_by", UUID.class),
                        rows.getObject("uploaded_at", OffsetDateTime.class).toInstant()));
            }
        }
        return documents;
    }

    private static List<FlywayHistory> readFlywayHistory(DataSource dataSource) throws SQLException {
        List<FlywayHistory> history = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rows = connection.createStatement().executeQuery("""
                     SELECT version, type, script, success
                     FROM flyway_schema_history
                     ORDER BY installed_rank
                     """)) {
            while (rows.next()) {
                history.add(new FlywayHistory(
                        rows.getString("version"),
                        rows.getString("type"),
                        rows.getString("script"),
                        rows.getBoolean("success")));
            }
        }
        return history;
    }

    private static List<Integer> readChatRecordCounts(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("""
                     SELECT
                         (SELECT count(*) FROM chat_sessions WHERE id = ?) AS sessions,
                         (SELECT count(*) FROM chat_messages WHERE id = ? AND session_id = ?) AS messages
                     """)) {
            query.setObject(1, SESSION_ID);
            query.setObject(2, MESSAGE_ID);
            query.setObject(3, SESSION_ID);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return List.of(rows.getInt("sessions"), rows.getInt("messages"));
            }
        }
    }

    private static boolean errorMessageColumnIsNullableText(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("""
                     SELECT data_type, is_nullable
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'document_records'
                       AND column_name = 'error_message'
                     """);
             ResultSet row = query.executeQuery()) {
            return row.next()
                    && "text".equals(row.getString("data_type"))
                    && "YES".equals(row.getString("is_nullable"));
        }
    }

    private static void validateJpaSchema(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean entityManagerFactory =
                new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan(
                DocumentRecord.class.getPackageName(),
                ChatSession.class.getPackageName(),
                ChatMessage.class.getPackageName());
        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        try {
            entityManagerFactory.afterPropertiesSet();
            assertThat(entityManagerFactory.getObject()).isNotNull();
        } finally {
            entityManagerFactory.destroy();
        }
    }

    private record DocumentSnapshot(
            UUID id,
            String filename,
            String contentType,
            long sizeBytes,
            String status,
            int chunkCount,
            UUID uploadedBy,
            Instant uploadedAt) {

        private DocumentSnapshot afterStatusMigration() {
            return "UPLOADING".equals(status)
                    ? new DocumentSnapshot(id, filename, contentType, sizeBytes,
                    "UPLOADED", chunkCount, uploadedBy, uploadedAt)
                    : this;
        }
    }

    private record FlywayHistory(String version, String type, String script, boolean success) {
    }
}
