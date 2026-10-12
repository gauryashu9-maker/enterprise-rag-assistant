package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.DocumentRecord;
import com.axlero.ragassistant.entities.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentRecordResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int chunkCount,
        UUID uploadedBy,
        Instant uploadedAt) {

    public static DocumentRecordResponse fromEntity(DocumentRecord document) {
        return new DocumentRecordResponse(
                document.getId(), document.getFilename(), document.getContentType(),
                document.getSizeBytes(), document.getStatus(), document.getChunkCount(),
                document.getUploadedBy(), document.getUploadedAt());
    }
}
