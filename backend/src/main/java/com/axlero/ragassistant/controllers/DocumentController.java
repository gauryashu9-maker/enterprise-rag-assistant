package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.services.DocumentDeletionService;
import com.axlero.ragassistant.services.DocumentQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentQueryService documentQueryService;
    private final DocumentDeletionService documentDeletionService;

    @Autowired
    public DocumentController(
            DocumentQueryService documentQueryService,
            DocumentDeletionService documentDeletionService) {
        this.documentQueryService = documentQueryService;
        this.documentDeletionService = documentDeletionService;
    }

    public DocumentController(DocumentQueryService documentQueryService) {
        this(documentQueryService, null);
    }

    public DocumentController(DocumentDeletionService documentDeletionService) {
        this(null, documentDeletionService);
    }

    @GetMapping
    public List<DocumentResponse> listDocuments(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return documentQueryService.listDocuments(user);
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return documentQueryService.getDocument(id, user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        documentDeletionService.deleteDocument(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(
                "File received: " + file.getOriginalFilename());
    }
}
