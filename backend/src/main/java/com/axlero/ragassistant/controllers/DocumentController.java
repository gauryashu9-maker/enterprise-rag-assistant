
package com.axlero.ragassistant.controllers;

import com.axlero.ragassistant.services.DocumentService;
import com.axlero.ragassistant.services.TextChunkingService;
import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final TextChunkingService textChunkingService;

    public DocumentController(
            DocumentService documentService,
            TextChunkingService textChunkingService) {
        this.documentService = documentService;
        this.textChunkingService = textChunkingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(
            @RequestParam("file") MultipartFile file) {
        try {
            String text = documentService.extractText(file);
            List<String> chunks = textChunkingService.splitIntoChunks(text);

            return ResponseEntity.ok(
                    "Success! Extracted " + text.length()
                    + " characters and created " + chunks.size() + " chunks.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body("Validation Error: " + e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Error reading file content.");
        }
    }
}
