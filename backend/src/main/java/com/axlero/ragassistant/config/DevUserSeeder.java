package com.axlero.ragassistant.config;

import com.axlero.ragassistant.entities.Role;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds demo accounts for local development. The two USER accounts reuse the owner ids that
 * DevDataSeeder uses for its documents, so logging in as them shows those documents.
 * Runs only with the "dev" profile. Dev passwords are listed in the README.
 */
@Component
@Profile("dev")
public class DevUserSeeder implements CommandLineRunner {

    static final UUID DEV_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ad");

    static final String INSERT_SQL =
            "INSERT INTO public.users (id, email, password_hash, role) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed(DevDataSeeder.DEV_OWNER_ID, "owner@example.com", "secret123", Role.USER);
        seed(DevDataSeeder.DEV_OTHER_OWNER_ID, "other@example.com", "secret123", Role.USER);
        seed(DEV_ADMIN_ID, "admin@example.com", "admin12345", Role.ADMIN);
    }

    private void seed(UUID id, String email, String password, Role role) {
        int inserted = jdbcTemplate.update(INSERT_SQL, id, email, passwordEncoder.encode(password), role.name());
        if (inserted == 1) {
            log.info("Seeded dev account {} ({})", email, role);
        }
    }
}