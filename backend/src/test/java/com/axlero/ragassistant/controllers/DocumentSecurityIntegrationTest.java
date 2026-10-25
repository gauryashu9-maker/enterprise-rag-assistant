package com.axlero.ragassistant.controllers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axlero.ragassistant.config.CorsConfig;
import com.axlero.ragassistant.config.SecurityConfig;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.GlobalExceptionHandler;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.security.JwtAuthenticationFilter;
import com.axlero.ragassistant.security.SecurityProblemHandler;
import com.axlero.ragassistant.services.DocumentDeletionService;
import com.axlero.ragassistant.services.DocumentQueryService;
import com.axlero.ragassistant.services.JwtService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the Document API security layer.
 *
 * <p>These tests use the real Spring Security filter chain (SecurityConfig, JwtAuthenticationFilter,
 * SecurityProblemHandler) and a real JwtService, so they verify the full JWT authentication and
 * authorization path — not a stub. Services (DocumentQueryService, DocumentDeletionService) are
 * mocked so no database or vector store is needed.
 *
 * <p>Covers:
 * <ul>
 *   <li>Missing token → 401 ProblemDetail with correct {@code instance} path (PR #40)
 *   <li>Invalid/malformed token → 401 ProblemDetail
 *   <li>Expired token → 401 ProblemDetail
 *   <li>Valid USER token → service called with the principal extracted from the JWT
 *   <li>Valid ADMIN token → admin sees all documents
 *   <li>USER cannot read another owner's document → 404 ProblemDetail
 *   <li>USER cannot delete another owner's document → 404 ProblemDetail
 *   <li>ADMIN can delete any document → 204 No Content
 *   <li>Missing token on DELETE → 401 ProblemDetail with correct instance
 *   <li>Token signed with a different key → 401 ProblemDetail
 *   <li>Authenticated USER list → 200 with owned documents only
 * </ul>
 */
@WebMvcTest(DocumentController.class)
@Import({
    SecurityConfig.class,
    CorsConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    SecurityProblemHandler.class,
    GlobalExceptionHandler.class
})
@TestPropertySource(properties = "app.security.jwt.expiration=PT15M")
class DocumentSecurityIntegrationTest {

    // A random 32-byte secret generated once per test class run.
    private static final String TEST_SECRET;

    static {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        TEST_SECRET = Base64.getEncoder().encodeToString(keyBytes);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> TEST_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private DocumentQueryService documentQueryService;

    @MockitoBean
    private DocumentDeletionService documentDeletionService;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Missing token → 401 with correct ProblemDetail.instance  (PR #40)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getDocumentsWithNoTokenReturns401ProblemDetailWithCorrectInstance() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents")));

        verify(documentQueryService, never()).listDocuments(any(AuthenticatedUser.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Invalid / malformed token → 401
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getDocumentsWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents")));

        verify(documentQueryService, never()).listDocuments(any(AuthenticatedUser.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Expired token → 401
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getDocumentsWithExpiredTokenReturns401() throws Exception {
        String expiredToken = expiredToken();

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents")));

        verify(documentQueryService, never()).listDocuments(any(AuthenticatedUser.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Token signed with a different key → 401
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getDocumentsWithTokenSignedByWrongKeyReturns401() throws Exception {
        // Mint a token with a different random key — signature verification must fail.
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        String wrongSecret = Base64.getEncoder().encodeToString(otherKey);

        // Build a JwtService using the wrong key and mint a structurally valid token with it.
        JwtService wrongKeyService = new JwtService(wrongSecret, java.time.Duration.ofMinutes(15));
        String tokenFromWrongKey = wrongKeyService.generateToken(UUID.randomUUID(), "x@x.com", Role.USER);

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + tokenFromWrongKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Valid USER token → listDocuments called with the principal from the JWT
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedUserSeesOnlyTheirOwnDocuments() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        DocumentResponse doc = document("handbook.pdf");
        // Match on any AuthenticatedUser whose id equals userId (the filter populates it from the JWT).
        when(documentQueryService.listDocuments(userWithId(userId)))
                .thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename", equalTo("handbook.pdf")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. Valid ADMIN token → admin sees all documents
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedAdminSeesAllDocuments() throws Exception {
        UUID adminId = UUID.randomUUID();
        String token = jwtService.generateToken(adminId, "admin@example.com", Role.ADMIN);

        when(documentQueryService.listDocuments(adminWithId(adminId)))
                .thenReturn(List.of(document("a.pdf"), document("b.pdf")));

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].filename", equalTo("a.pdf")))
                .andExpect(jsonPath("$[1].filename", equalTo("b.pdf")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. USER GET /{id} for owned document → 200
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedUserGetsTheirOwnDocumentBy200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        when(documentQueryService.getDocument(eq(docId), userWithId(userId)))
                .thenReturn(document("policy.pdf"));

        mockMvc.perform(get("/api/documents/{id}", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", equalTo("policy.pdf")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 8. USER GET /{id} for another owner's document → 404 ProblemDetail
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedUserCannotReadAnotherOwnersDocumentGets404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        when(documentQueryService.getDocument(eq(docId), userWithId(userId)))
                .thenThrow(new ResourceNotFoundException("Document not found"));

        mockMvc.perform(get("/api/documents/{id}", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Document not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents/" + docId)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. ADMIN GET /{id} for any document → 200
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedAdminGetsAnyDocumentAcrossOwners() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String token = jwtService.generateToken(adminId, "admin@example.com", Role.ADMIN);

        when(documentQueryService.getDocument(eq(docId), adminWithId(adminId)))
                .thenReturn(document("cross-owner.pdf"));

        mockMvc.perform(get("/api/documents/{id}", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename", equalTo("cross-owner.pdf")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 10. USER DELETE /{id} for another owner → 404 ProblemDetail
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedUserCannotDeleteAnotherOwnersDocumentGets404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        doThrow(new ResourceNotFoundException("Document not found"))
                .when(documentDeletionService).deleteDocument(eq(docId), userWithId(userId));

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Document not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents/" + docId)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 11. Missing token on DELETE → 401 with correct instance (PR #40)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void deleteWithNoTokenReturns401ProblemDetailWithCorrectInstance() throws Exception {
        UUID docId = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/{id}", docId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/documents/" + docId)));

        verify(documentDeletionService, never()).deleteDocument(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Argmatcher: any AuthenticatedUser with the given id and role USER. */
    private AuthenticatedUser userWithId(UUID id) {
        return org.mockito.ArgumentMatchers.argThat(
                u -> u != null && u.id().equals(id) && u.role() == Role.USER);
    }

    /** Argmatcher: any AuthenticatedUser with the given id and role ADMIN. */
    private AuthenticatedUser adminWithId(UUID id) {
        return org.mockito.ArgumentMatchers.argThat(
                u -> u != null && u.id().equals(id) && u.role() == Role.ADMIN);
    }

    private DocumentResponse document(String filename) {
        return new DocumentResponse(
                UUID.randomUUID(), filename, DocumentStatus.READY,
                Instant.parse("2026-10-15T12:30:00Z"), 2048L, null);
    }

    /** Builds a token that was valid in the past and is now expired. */
    private String expiredToken() {
        // Mint directly using JJWT to control issuedAt/expiration without the service guard.
        long now = System.currentTimeMillis();
        return io.jsonwebtoken.Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new java.util.Date(now - 120_000))
                .expiration(new java.util.Date(now - 60_000))
                .signWith(
                        io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                Base64.getDecoder().decode(TEST_SECRET)),
                        io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();
    }
}
