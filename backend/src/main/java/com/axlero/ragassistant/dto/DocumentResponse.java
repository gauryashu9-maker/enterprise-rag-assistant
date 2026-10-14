package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        UUID id,
        String filename,
        DocumentStatus status,
        Instant uploadedAt,
        Long sizeBytes,
        String errorMessage) {

    public static DocumentResponse fromEntity(DocumentRecord document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getUploadedAt(),
                document.getSizeBytes(),
                document.getErrorMessage());
    }
}
