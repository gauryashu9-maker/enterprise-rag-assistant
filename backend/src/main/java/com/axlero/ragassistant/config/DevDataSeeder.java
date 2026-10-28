package com.axlero.ragassistant.config;

import com.axlero.ragassistant.services.ChunkMetadata;
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

/** DEV ONLY: loads sample chunks, each owned by a fixed dev user, so retrieval and owner scoping can be tested locally. */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    /** Owner of the HR sample chunks. Mint a local test token with this UUID as the subject. */
    public static final UUID DEV_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    /** A second user, used to prove one user cannot retrieve another user's chunks. */
    public static final UUID DEV_OTHER_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final VectorStore vectorStore;

    public DevDataSeeder(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @SuppressWarnings("null")
    @Override
    public void run(String... args) {
        List<Document> chunks = List.of(
                chunk(DEV_OWNER_ID, "hr-policy.pdf", "Employees get 24 days of paid annual leave per calendar year. Up to 5 unused days can carry over.", 1),
                chunk(DEV_OWNER_ID, "hr-policy.pdf", "Remote work is allowed up to 3 days per week with prior manager approval.", 2),
                chunk(DEV_OWNER_ID, "hr-policy.pdf", "Expense claims must be submitted within 30 days of purchase, with original receipts attached.", 3),
                chunk(DEV_OWNER_ID, "hr-policy.pdf", "The company laptop must be returned within 7 days after the last working day.", 4),
                chunk(DEV_OTHER_OWNER_ID, "exec-bonus.pdf", "Executives receive a yearly bonus of 40 percent of their base salary.", 1));
        vectorStore.add(chunks);
        log.info("Seeded {} sample chunks into the vector store", chunks.size());
    }

    // Deterministic id, so restarting the app re-writes the same rows instead of duplicating them.
    @SuppressWarnings("null")
    private Document chunk(UUID ownerId, String filename, String text, int page) {
        String id = UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString();
        return new Document(id, text, Map.of(
                ChunkMetadata.FILENAME, filename,
                ChunkMetadata.PAGE, page,
                ChunkMetadata.OWNER_ID, ownerId.toString()));
    }
}
