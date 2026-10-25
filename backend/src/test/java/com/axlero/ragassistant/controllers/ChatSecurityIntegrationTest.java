package com.axlero.ragassistant.controllers;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axlero.ragassistant.config.CorsConfig;
import com.axlero.ragassistant.config.SecurityConfig;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.dto.ChatMessageResponse;
import com.axlero.ragassistant.dto.ChatSessionResponse;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.GlobalExceptionHandler;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.security.JwtAuthenticationFilter;
import com.axlero.ragassistant.security.SecurityProblemHandler;
import com.axlero.ragassistant.services.ChatConversationQueryService;
import com.axlero.ragassistant.services.ChatSessionService;
import com.axlero.ragassistant.services.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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
 * Integration tests for the Chat API security layer.
 *
 * <p>These tests exercise the real Spring Security filter chain (SecurityConfig, CorsConfig,
 * JwtAuthenticationFilter, SecurityProblemHandler) and JwtService against {@link ChatController}.
 * Services ({@link ChatSessionService}, {@link ChatConversationQueryService}) are mocked so no
 * database or Docker daemon is required.
 *
 * <p>Covers:
 * <ul>
 *   <li>Missing token on POST /api/chat/sessions &rarr; 401 ProblemDetail with correct instance
 *   <li>Missing token on GET /api/chat/sessions &rarr; 401 ProblemDetail with correct instance
 *   <li>Missing token on GET /api/chat/sessions/{id} &rarr; 401 ProblemDetail with correct instance
 *   <li>Expired JWT token &rarr; 401 ProblemDetail
 *   <li>Invalid / corrupted JWT token &rarr; 401 ProblemDetail
 *   <li>Token signed with a different HMAC secret &rarr; 401 ProblemDetail
 *   <li>Authorization header missing Bearer prefix &rarr; 401 ProblemDetail
 *   <li>Valid USER token creates session &rarr; 201 Created with Location header and principal
 *   <li>Valid USER token lists owned sessions &rarr; 200 OK with owned sessions only
 *   <li>Valid USER token fetches owned conversation &rarr; 200 OK with session and messages
 *   <li>Cross-user access: User B requesting User A's session &rarr; 404 ProblemDetail (masks existence)
 *   <li>Admin isolation: Admin requesting another user's conversation &rarr; 404 ProblemDetail (strict privacy)
 *   <li>Nonexistent session requested by authenticated user &rarr; 404 ProblemDetail
 * </ul>
 */
@WebMvcTest(ChatController.class)
@Import({
    SecurityConfig.class,
    CorsConfig.class,
    JwtAuthenticationFilter.class,
    JwtService.class,
    SecurityProblemHandler.class,
    GlobalExceptionHandler.class
})
@TestPropertySource(properties = "app.security.jwt.expiration=PT15M")
class ChatSecurityIntegrationTest {

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
    private ChatSessionService chatSessionService;

    @MockitoBean
    private ChatConversationQueryService chatConversationQueryService;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Missing Token Tests (401 Unauthorized with RFC 7807 ProblemDetail)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void createSessionWithNoTokenReturns401ProblemDetailWithCorrectInstance() throws Exception {
        mockMvc.perform(post("/api/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.detail", equalTo("Authentication is required or the bearer token is invalid")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).createSession(any());
    }

    @Test
    void listSessionsWithNoTokenReturns401ProblemDetailWithCorrectInstance() throws Exception {
        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.detail", equalTo("Authentication is required or the bearer token is invalid")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).listSessions(any());
    }

    @Test
    void getConversationWithNoTokenReturns401ProblemDetailWithCorrectInstance() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.detail", equalTo("Authentication is required or the bearer token is invalid")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId)));

        verify(chatConversationQueryService, never()).getConversation(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Token Integrity & Lifecycle Rejections (Expired, Corrupted, Bad Secret)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void createSessionWithExpiredTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).createSession(any());
    }

    @Test
    void getConversationWithExpiredTokenReturns401ProblemDetail() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId)));

        verify(chatConversationQueryService, never()).getConversation(any(), any());
    }

    @Test
    void listSessionsWithMalformedTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer not.a.valid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).listSessions(any());
    }

    @Test
    void createSessionWithTokenSignedWithDifferentSecretReturns401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + tokenSignedWithDifferentSecret()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).createSession(any());
    }

    @Test
    void getConversationWithNonBearerAuthorizationHeaderReturns401ProblemDetail() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String validToken = jwtService.generateToken(UUID.randomUUID(), "user@example.com", Role.USER);

        // Non-Bearer header (e.g. Basic or raw token) must be rejected
        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId)
                        .header("Authorization", "Basic " + validToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId)));

        verify(chatConversationQueryService, never()).getConversation(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Authenticated Valid Operations (Principal extraction, 200/201 responses)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void authenticatedUserCreatesSessionSuccessfullyReturns201WithLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-25T12:00:00Z");

        when(chatSessionService.createSession(userWithId(userId)))
                .thenReturn(new ChatSessionResponse(sessionId, userId, createdAt));

        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/chat/sessions/" + sessionId)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", equalTo(sessionId.toString())))
                .andExpect(jsonPath("$.userId", equalTo(userId.toString())))
                .andExpect(jsonPath("$.createdAt", equalTo("2026-10-25T12:00:00Z")));

        verify(chatSessionService).createSession(userWithId(userId));
    }

    @Test
    void authenticatedUserListsOnlyOwnedSessionsReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();

        when(chatSessionService.listSessions(userWithId(userId)))
                .thenReturn(List.of(
                        new ChatSessionResponse(session1, userId, Instant.parse("2026-10-25T12:00:00Z")),
                        new ChatSessionResponse(session2, userId, Instant.parse("2026-10-25T11:00:00Z"))));

        mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", equalTo(session1.toString())))
                .andExpect(jsonPath("$[0].userId", equalTo(userId.toString())))
                .andExpect(jsonPath("$[1].id", equalTo(session2.toString())))
                .andExpect(jsonPath("$[1].userId", equalTo(userId.toString())));

        verify(chatSessionService).listSessions(userWithId(userId));
    }

    @Test
    void authenticatedUserRetrievesOwnedConversationReturns200WithMessages() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        ChatSessionResponse sessionResponse = new ChatSessionResponse(
                sessionId, userId, Instant.parse("2026-10-25T10:00:00Z"));
        List<ChatMessageResponse> messages = List.of(
                new ChatMessageResponse(
                        UUID.randomUUID(), sessionId, ChatMessageRole.USER, "What is the policy?", Instant.parse("2026-10-25T10:00:01Z")),
                new ChatMessageResponse(
                        UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, "Here is the policy.", Instant.parse("2026-10-25T10:00:02Z")));

        when(chatConversationQueryService.getConversation(eq(sessionId), userWithId(userId)))
                .thenReturn(new ChatConversationResponse(sessionResponse, messages));

        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.session.id", equalTo(sessionId.toString())))
                .andExpect(jsonPath("$.session.userId", equalTo(userId.toString())))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role", equalTo("USER")))
                .andExpect(jsonPath("$.messages[0].content", equalTo("What is the policy?")))
                .andExpect(jsonPath("$.messages[1].role", equalTo("ASSISTANT")))
                .andExpect(jsonPath("$.messages[1].content", equalTo("Here is the policy.")));

        verify(chatConversationQueryService).getConversation(eq(sessionId), userWithId(userId));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Cross-User and Admin Isolation Tests (404 Not Found masks existence)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void crossUserCannotAccessOtherUsersConversationReturns404ProblemDetail() throws Exception {
        UUID attackerUserId = UUID.randomUUID();
        UUID victimSessionId = UUID.randomUUID();
        String attackerToken = jwtService.generateToken(attackerUserId, "attacker@example.com", Role.USER);

        // Security boundary: querying another owner's session throws ResourceNotFoundException (404, never 403)
        when(chatConversationQueryService.getConversation(eq(victimSessionId), userWithId(attackerUserId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(get("/api/chat/sessions/{id}", victimSessionId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + victimSessionId)));

        verify(chatConversationQueryService).getConversation(eq(victimSessionId), userWithId(attackerUserId));
    }

    @Test
    void adminCannotAccessUserConversationReturns404ProblemDetail() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID userSessionId = UUID.randomUUID();
        String adminToken = jwtService.generateToken(adminId, "admin@example.com", Role.ADMIN);

        // Strict privacy contract: unlike documents, chat conversations are private and ADMIN cannot inspect them
        when(chatConversationQueryService.getConversation(eq(userSessionId), adminWithId(adminId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(get("/api/chat/sessions/{id}", userSessionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + userSessionId)));

        verify(chatConversationQueryService).getConversation(eq(userSessionId), adminWithId(adminId));
    }

    @Test
    void nonExistentSessionReturns404ProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID missingSessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        when(chatConversationQueryService.getConversation(eq(missingSessionId), userWithId(userId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(get("/api/chat/sessions/{id}", missingSessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + missingSessionId)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser userWithId(UUID id) {
        return org.mockito.ArgumentMatchers.argThat(
                u -> u != null && u.id().equals(id) && u.role() == Role.USER);
    }

    private AuthenticatedUser adminWithId(UUID id) {
        return org.mockito.ArgumentMatchers.argThat(
                u -> u != null && u.id().equals(id) && u.role() == Role.ADMIN);
    }

    private String expiredToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(
                        Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET)),
                        Jwts.SIG.HS256)
                .compact();
    }

    private String tokenSignedWithDifferentSecret() {
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .claim("role", Role.USER.name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .signWith(
                        Keys.hmacShaKeyFor(otherKey),
                        Jwts.SIG.HS256)
                .compact();
    }
}
