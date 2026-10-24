
package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.services.DocumentDeletionService;
import com.axlero.ragassistant.services.DocumentIngestionService;
import com.axlero.ragassistant.services.DocumentQueryService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentQueryService documentQueryService;
    private final DocumentDeletionService documentDeletionService;

    @Autowired
    public DocumentController(
            DocumentIngestionService documentIngestionService,
            DocumentQueryService documentQueryService,
            DocumentDeletionService documentDeletionService) {
        this.documentIngestionService = documentIngestionService;
        this.documentQueryService = documentQueryService;
        this.documentDeletionService = documentDeletionService;
    }

    // Compatibility constructors for existing tests
    public DocumentController(DocumentIngestionService documentIngestionService) {
        this(documentIngestionService, null, null);
    }

    public DocumentController(
            DocumentQueryService documentQueryService,
            DocumentDeletionService documentDeletionService) {
        this(null, documentQueryService, documentDeletionService);
    }

    public DocumentController(DocumentQueryService documentQueryService) {
        this(null, documentQueryService, null);
    }

    public DocumentController(DocumentDeletionService documentDeletionService) {
        this(null, null, documentDeletionService);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            DocumentRecord record =
                    documentIngestionService.ingest(file, user.id());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(DocumentResponse.fromEntity(record));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Validation Error: " + e.getMessage());

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Error reading file content.");
        }
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
}
