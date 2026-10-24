package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.GlobalExceptionHandler;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.exceptions.ServiceUnavailableException;
import com.axlero.ragassistant.services.DocumentDeletionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DocumentDeleteControllerHttpTest {

    private final DocumentDeletionService documentDeletionService = mock(DocumentDeletionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new DocumentController(documentDeletionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletesDocumentForAuthenticatedOwnerAndReturns204NoContent() throws Exception {
        AuthenticatedUser owner = user(Role.USER);
        authenticate(owner);
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNoContent());

        verify(documentDeletionService).deleteDocument(documentId, owner);
    }

    @Test
    void deletesDocumentForAuthenticatedAdminAndReturns204NoContent() throws Exception {
        AuthenticatedUser admin = user(Role.ADMIN);
        authenticate(admin);
        UUID documentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNoContent());

        verify(documentDeletionService).deleteDocument(documentId, admin);
    }

    @Test
    void returns404ProblemDetailWhenDocumentNotFoundOrCrossOwner() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        UUID documentId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Document not found"))
                .when(documentDeletionService).deleteDocument(documentId, user);

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Document not found"));
    }

    @Test
    void returns503ProblemDetailWhenVectorStoreFails() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        UUID documentId = UUID.randomUUID();
        doThrow(new ServiceUnavailableException("Vector store error", new RuntimeException("connection lost")))
                .when(documentDeletionService).deleteDocument(documentId, user);

        mockMvc.perform(delete("/api/documents/{id}", documentId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("A required service is temporarily unavailable"));
    }

    private void authenticate(AuthenticatedUser user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase() + "@example.com", role);
    }
}
