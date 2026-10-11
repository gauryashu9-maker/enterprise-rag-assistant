package com.axlero.ragassistant.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** DEV ONLY: loads sample chunks so retrieval can be tested before the ingestion pipeline exists. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final VectorStore vectorStore;

    public DevDataSeeder(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @SuppressWarnings("null")
    @Override
    public void run(String... args) {
        List<Document> chunks = List.of(
                chunk("Employees get 24 days of paid annual leave per calendar year. Up to 5 unused days can carry over.", 1),
                chunk("Remote work is allowed up to 3 days per week with prior manager approval.", 2),
                chunk("Expense claims must be submitted within 30 days of purchase, with original receipts attached.", 3),
                chunk("The company laptop must be returned within 7 days after the last working day.", 4));
        vectorStore.add(chunks);
        log.info("Seeded {} sample chunks into the vector store", chunks.size());
    }

    // Deterministic id, so restarting the app re-writes the same rows instead of duplicating them.
    @SuppressWarnings("null")
    private Document chunk(String text, int page) {
        String id = UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString();
        return new Document(id, text, Map.of("filename", "hr-policy.pdf", "page", page));
    }
}