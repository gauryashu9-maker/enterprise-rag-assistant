package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.config.CorsConfig;
import com.axlero.ragassistant.config.SecurityConfig;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.ChatConversationResponse;
import com.axlero.ragassistant.dto.ChatMessageResponse;
import com.axlero.ragassistant.dto.ChatSessionResponse;
import com.axlero.ragassistant.dto.Citation;
import com.axlero.ragassistant.dto.SendChatMessageResponse;
import com.axlero.ragassistant.entities.ChatMessageRole;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.GlobalExceptionHandler;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.security.JwtAuthenticationFilter;
import com.axlero.ragassistant.security.SecurityProblemHandler;
import com.axlero.ragassistant.services.ChatConversationQueryService;
import com.axlero.ragassistant.services.ChatMessageService;
import com.axlero.ragassistant.services.ChatSessionService;
import com.axlero.ragassistant.services.JwtService;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
class ChatControllerHttpTest {

    private static final String TEST_SECRET;

    static {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        TEST_SECRET = Base64.getEncoder().encodeToString(keyBytes);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt.secret", () -> TEST_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ChatSessionService chatSessionService;

    @MockitoBean
    private ChatConversationQueryService chatConversationQueryService;

    @MockitoBean
    private ChatMessageService chatMessageService;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. POST /api/chat/sessions
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void createSessionReturns201WithLocationHeaderAndSessionBody() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-24T10:00:00Z");

        when(chatSessionService.createSession(userWithId(userId)))
                .thenReturn(new ChatSessionResponse(sessionId, userId, createdAt));

        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/chat/sessions/" + sessionId)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", equalTo(sessionId.toString())))
                .andExpect(jsonPath("$.userId", equalTo(userId.toString())))
                .andExpect(jsonPath("$.createdAt", equalTo(createdAt.toString())));

        verify(chatSessionService).createSession(userWithId(userId));
    }

    @Test
    void createSessionWithNoTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).createSession(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. GET /api/chat/sessions (newest first)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void listSessionsReturns200WithSessionsInNewestFirstOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        UUID session1 = UUID.randomUUID();
        UUID session2 = UUID.randomUUID();

        when(chatSessionService.listSessions(userWithId(userId)))
                .thenReturn(List.of(
                        new ChatSessionResponse(session1, userId, Instant.parse("2026-10-24T12:00:00Z")),
                        new ChatSessionResponse(session2, userId, Instant.parse("2026-10-24T10:00:00Z"))));

        mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", equalTo(session1.toString())))
                .andExpect(jsonPath("$[1].id", equalTo(session2.toString())));

        verify(chatSessionService).listSessions(userWithId(userId));
    }

    @Test
    void listSessionsReturnsEmptyArrayWhenNoSessionsFound() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        when(chatSessionService.listSessions(userWithId(userId)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));

        verify(chatSessionService).listSessions(userWithId(userId));
    }

    @Test
    void listSessionsWithNoTokenReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions")));

        verify(chatSessionService, never()).listSessions(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. GET /api/chat/sessions/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getConversationReturns200WithSessionAndOrderedMessages() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        ChatSessionResponse sessionResponse = new ChatSessionResponse(
                sessionId, userId, Instant.parse("2026-10-24T10:00:00Z"));
        List<ChatMessageResponse> messages = List.of(
                new ChatMessageResponse(
                        UUID.randomUUID(), sessionId, ChatMessageRole.USER, "Hello", Instant.parse("2026-10-24T10:00:01Z")),
                new ChatMessageResponse(
                        UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, "Hi there!", Instant.parse("2026-10-24T10:00:02Z")));

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
                .andExpect(jsonPath("$.messages[0].content", equalTo("Hello")))
                .andExpect(jsonPath("$.messages[1].role", equalTo("ASSISTANT")))
                .andExpect(jsonPath("$.messages[1].content", equalTo("Hi there!")));

        verify(chatConversationQueryService).getConversation(eq(sessionId), userWithId(userId));
    }

    @Test
    void getConversationForMissingOrCrossOwnerSessionReturns404ProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        when(chatConversationQueryService.getConversation(eq(sessionId), userWithId(userId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId)));
    }

    @Test
    void getConversationForAdminAcrossOwnersReturns404ProblemDetail() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID otherUserSessionId = UUID.randomUUID();
        String token = jwtService.generateToken(adminId, "admin@example.com", Role.ADMIN);

        // Strict owner isolation: even ADMIN querying another user's conversation throws 404
        when(chatConversationQueryService.getConversation(eq(otherUserSessionId), adminWithId(adminId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(get("/api/chat/sessions/{id}", otherUserSessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + otherUserSessionId)));
    }

    @Test
    void getConversationWithNoTokenReturns401ProblemDetail() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId)));

        verify(chatConversationQueryService, never()).getConversation(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. POST /api/chat/sessions/{id}/messages
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void sendMessageReturns200WithMessagesAndCitations() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        String question = "What is the policy?";
        String answer = "The policy allows 24 days off.";

        ChatMessageResponse userMsg = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.USER, question, Instant.parse("2026-10-24T10:00:00Z"));
        ChatMessageResponse assistantMsg = new ChatMessageResponse(
                UUID.randomUUID(), sessionId, ChatMessageRole.ASSISTANT, answer, Instant.parse("2026-10-24T10:00:01Z"));
        List<Citation> citations = List.of(new Citation("policy.pdf", 1, "24 days off"));

        when(chatMessageService.sendMessage(eq(sessionId), eq(question), userWithId(userId)))
                .thenReturn(new SendChatMessageResponse(userMsg, assistantMsg, citations));

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + question + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userMessage.content", equalTo(question)))
                .andExpect(jsonPath("$.userMessage.role", equalTo("USER")))
                .andExpect(jsonPath("$.assistantMessage.content", equalTo(answer)))
                .andExpect(jsonPath("$.assistantMessage.role", equalTo("ASSISTANT")))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].documentName", equalTo("policy.pdf")));

        verify(chatMessageService).sendMessage(eq(sessionId), eq(question), userWithId(userId));
    }

    @Test
    void sendMessageWithNoTokenReturns401ProblemDetail() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId + "/messages")));

        verify(chatMessageService, never()).sendMessage(any(), any(), any());
    }

    @Test
    void sendMessageWithBlankMessageReturns400ValidationProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.title", equalTo("Validation failed")))
                .andExpect(jsonPath("$.errors.message", equalTo("must not be blank")));

        verify(chatMessageService, never()).sendMessage(any(), any(), any());
    }

    @Test
    void sendMessageWithMissingBodyReturns400MalformedProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.title", equalTo("Malformed request")));

        verify(chatMessageService, never()).sendMessage(any(), any(), any());
    }

    @Test
    void sendMessageForMissingOrCrossOwnerSessionReturns404ProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        String question = "Can I work remotely?";

        when(chatMessageService.sendMessage(eq(sessionId), eq(question), userWithId(userId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + question + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId + "/messages")));
    }

    @Test
    void sendMessageForAdminAcrossOwnersReturns404ProblemDetail() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID otherUserSessionId = UUID.randomUUID();
        String token = jwtService.generateToken(adminId, "admin@example.com", Role.ADMIN);
        String question = "Admin trying cross-owner message";

        when(chatMessageService.sendMessage(eq(otherUserSessionId), eq(question), adminWithId(adminId)))
                .thenThrow(new ResourceNotFoundException("Conversation not found"));

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", otherUserSessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + question + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Resource not found")))
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.detail", equalTo("Conversation not found")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + otherUserSessionId + "/messages")));
    }

    @Test
    void sendMessageWhenRagFailsReturns500ProblemDetail() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        String question = "Prompt causing RAG error";

        when(chatMessageService.sendMessage(eq(sessionId), eq(question), userWithId(userId)))
                .thenThrow(new IllegalStateException("RAG LLM service failure"));

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + question + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title", equalTo("Internal server error")))
                .andExpect(jsonPath("$.status", equalTo(500)))
                .andExpect(jsonPath("$.detail", equalTo("An unexpected error occurred")))
                .andExpect(jsonPath("$.instance", equalTo("/api/chat/sessions/" + sessionId + "/messages")));
    }

    @Test
    void sendMessageWhenPersistenceFailsReturns200WithNullMessageIds() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.generateToken(userId, "user@example.com", Role.USER);
        String question = "What is the policy?";
        String answer = "The policy states 30 days notice.";
        Instant now = Instant.parse("2026-10-24T10:00:00Z");

        ChatMessageResponse userMsg = new ChatMessageResponse(null, sessionId, ChatMessageRole.USER, question, now);
        ChatMessageResponse assistantMsg = new ChatMessageResponse(null, sessionId, ChatMessageRole.ASSISTANT, answer, now.plusMillis(1));
        List<Citation> citations = List.of(new Citation("handbook.pdf", 1, "30 days notice"));
        SendChatMessageResponse response = new SendChatMessageResponse(userMsg, assistantMsg, citations);

        when(chatMessageService.sendMessage(eq(sessionId), eq(question), userWithId(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + question + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.userMessage.id", nullValue()))
                .andExpect(jsonPath("$.userMessage.role", equalTo("USER")))
                .andExpect(jsonPath("$.userMessage.content", equalTo(question)))
                .andExpect(jsonPath("$.assistantMessage.id", nullValue()))
                .andExpect(jsonPath("$.assistantMessage.role", equalTo("ASSISTANT")))
                .andExpect(jsonPath("$.assistantMessage.content", equalTo(answer)))
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].documentName", equalTo("handbook.pdf")));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private AuthenticatedUser userWithId(UUID id) {
        return argThat(u -> u != null && u.id().equals(id) && u.role() == Role.USER);
    }

    private AuthenticatedUser adminWithId(UUID id) {
        return argThat(u -> u != null && u.id().equals(id) && u.role() == Role.ADMIN);
    }
}
