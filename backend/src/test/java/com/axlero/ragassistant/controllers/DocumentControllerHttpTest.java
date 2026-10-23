package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.GlobalExceptionHandler;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.services.DocumentQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DocumentControllerHttpTest {

    private final DocumentQueryService documentQueryService = mock(DocumentQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new DocumentController(documentQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsDocumentsForAuthenticatedUser() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        when(documentQueryService.listDocuments(user)).thenReturn(List.of(document("handbook.pdf")));

        mockMvc.perform(get("/api/documents").param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("handbook.pdf"));

        verify(documentQueryService).listDocuments(user);
    }

    @Test
    void listsAllDocumentsForAuthenticatedAdmin() throws Exception {
        AuthenticatedUser admin = user(Role.ADMIN);
        authenticate(admin);
        when(documentQueryService.listDocuments(admin))
                .thenReturn(List.of(document("user-document.pdf"), document("another-document.pdf")));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("user-document.pdf"))
                .andExpect(jsonPath("$[1].filename").value("another-document.pdf"));

        verify(documentQueryService).listDocuments(admin);
    }

    @Test
    void getsDocumentUsingAuthenticatedPrincipal() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        UUID documentId = UUID.randomUUID();
        when(documentQueryService.getDocument(documentId, user)).thenReturn(document("handbook.pdf"));

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("handbook.pdf"));

        verify(documentQueryService).getDocument(documentId, user);
    }

    @Test
    void returnsNotFoundForDocumentOutsideAuthenticatedUsersOwnership() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        UUID documentId = UUID.randomUUID();
        when(documentQueryService.getDocument(documentId, user))
                .thenThrow(new ResourceNotFoundException("Document not found"));

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found"));
    }

    @Test
    void getsDocumentForAuthenticatedAdminAcrossOwners() throws Exception {
        AuthenticatedUser admin = user(Role.ADMIN);
        authenticate(admin);
        UUID documentId = UUID.randomUUID();
        when(documentQueryService.getDocument(documentId, admin))
                .thenReturn(document("cross-owner.pdf"));

        mockMvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("cross-owner.pdf"));

        verify(documentQueryService).getDocument(documentId, admin);
    }

    @Test
    @Disabled("Pending Security PR #39 merge: 401 Unauthorized regression test requires stateless JWT filter chain and SecurityConfig from #39. Do not fake with permit-all.")
    void unauthenticatedGetReturns401Unauthorized() throws Exception {
        // This test documents the pending dependency on Security PR #39.
        // Once PR #39 merges into main, this test will execute against the real
        // Spring Security filter chain to verify unauthenticated GET returns 401.
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    private void authenticate(AuthenticatedUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase() + "@example.com", role);
    }

    private DocumentResponse document(String filename) {
        return new DocumentResponse(
                UUID.randomUUID(), filename, DocumentStatus.READY, Instant.parse("2026-10-15T12:30:00Z"),
                2048L, null);
    }
}
