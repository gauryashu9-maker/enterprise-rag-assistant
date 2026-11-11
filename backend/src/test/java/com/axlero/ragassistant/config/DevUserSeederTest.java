package com.axlero.ragassistant.config;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevUserSeederTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void seedsTheTwoDocumentOwnersAndAnAdminWithHashedPasswords() {
        when(passwordEncoder.encode("secret123")).thenReturn("USER_HASH");
        when(passwordEncoder.encode("admin12345")).thenReturn("ADMIN_HASH");

        new DevUserSeeder(jdbcTemplate, passwordEncoder).run();

        verify(jdbcTemplate).update(eq(DevUserSeeder.INSERT_SQL),
                eq(DevDataSeeder.DEV_OWNER_ID), eq("owner@example.com"), eq("USER_HASH"), eq("USER"));
        verify(jdbcTemplate).update(eq(DevUserSeeder.INSERT_SQL),
                eq(DevDataSeeder.DEV_OTHER_OWNER_ID), eq("other@example.com"), eq("USER_HASH"), eq("USER"));
        verify(jdbcTemplate).update(eq(DevUserSeeder.INSERT_SQL),
                eq(DevUserSeeder.DEV_ADMIN_ID), eq("admin@example.com"), eq("ADMIN_HASH"), eq("ADMIN"));
    }
}