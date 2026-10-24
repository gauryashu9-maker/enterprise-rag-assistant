package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.dto.DocumentResponse;
import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.dto.AuthenticatedUser;
import com.axlero.ragassistant.services.DocumentIngestionService;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    public DocumentController(
            DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
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
}