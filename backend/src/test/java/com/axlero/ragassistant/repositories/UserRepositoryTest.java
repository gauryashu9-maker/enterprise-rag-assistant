package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserRepository users;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesUserAndFindsItByEmail() {
        User saved = users.saveAndFlush(new User("alice@example.com", "hash", Role.USER));
        entityManager.clear();

        User found = users.findByEmail("alice@example.com").orElseThrow();

        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getPasswordHash()).isEqualTo("hash");
        assertThat(found.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void findByEmailIsEmptyForUnknownEmail() {
        assertThat(users.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateEmail() {
        users.saveAndFlush(new User("dup@example.com", "h1", Role.USER));

        assertThatThrownBy(() -> users.saveAndFlush(new User("dup@example.com", "h2", Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storesRoleAsTextNotAnOrdinal() {
        User saved = users.saveAndFlush(new User("admin@example.com", "hash", Role.ADMIN));
        entityManager.clear();

        Object stored = entityManager.getEntityManager()
                .createNativeQuery("select role from public.users where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(stored).isEqualTo("ADMIN");
    }
}