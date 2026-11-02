package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.exceptions.GlobalExceptionHandler;
import com.axlero.ragassistant.exceptions.ResourceNotFoundException;
import com.axlero.ragassistant.services.DocumentDeletionService;
import com.axlero.ragassistant.services.DocumentIngestionService;
import com.axlero.ragassistant.services.DocumentQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DocumentControllerHttpTest {

    private final DocumentQueryService documentQueryService = mock(DocumentQueryService.class);
    private final DocumentIngestionService documentIngestionService = mock(DocumentIngestionService.class);
    private final DocumentDeletionService documentDeletionService = mock(DocumentDeletionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new DocumentController(documentIngestionService, documentQueryService, documentDeletionService))
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
    void uploadsDocumentSuccessfullyForAuthenticatedUser() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        UUID documentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "handbook.pdf", "application/pdf", "%PDF-1.4 sample content".getBytes());
        DocumentRecord record = new DocumentRecord(
                "handbook.pdf", "application/pdf", file.getSize(), DocumentStatus.READY, 2, user.id());
        ReflectionTestUtils.setField(record, "id", documentId);

        when(documentIngestionService.ingest(any(), eq(user.id()))).thenReturn(record);

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.filename").value("handbook.pdf"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sizeBytes").value((int) file.getSize()));

        verify(documentIngestionService).ingest(any(), eq(user.id()));
    }

    @Test
    void returnsBadRequestProblemWhenUploadValidationFails() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);
        MockMultipartFile corruptFile = new MockMultipartFile(
                "file", "corrupt.pdf", "application/pdf", "%PDF-1.4 invalid".getBytes());

        when(documentIngestionService.ingest(any(), eq(user.id())))
                .thenThrow(new IllegalArgumentException("The PDF could not be read. It may be corrupted or password-protected."));

        mockMvc.perform(multipart("/api/documents/upload").file(corruptFile))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("The PDF could not be read. It may be corrupted or password-protected."))
                .andExpect(jsonPath("$.instance").value("/api/documents/upload"));
    }

    @Test
    void returnsBadRequestProblemWhenUploadMultipartFilePartIsMissing() throws Exception {
        AuthenticatedUser user = user(Role.USER);
        authenticate(user);

        mockMvc.perform(multipart("/api/documents/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/documents/upload"));
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
