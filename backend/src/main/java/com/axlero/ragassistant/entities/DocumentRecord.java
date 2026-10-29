
package com.axlero.ragassistant.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "document_records")
public class DocumentRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false)
    private UUID uploadedBy;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    protected DocumentRecord() {
    }

    public DocumentRecord(
            String filename,
            String contentType,
            long sizeBytes,
            DocumentStatus status,
            int chunkCount,
            UUID uploadedBy) {
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.chunkCount = chunkCount;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
        this.retryCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void updateStatus(
            DocumentStatus status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public void updateChunkCount(int chunkCount) {
        if (chunkCount < 0) {
            throw new IllegalArgumentException(
                    "Chunk count cannot be negative.");
        }
        this.chunkCount = chunkCount;
    }

    public void markProcessing(Instant retryAt) {
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
        this.nextRetryAt = retryAt;
    }

    public void markReady(int count) {
        updateChunkCount(count);
        this.status = DocumentStatus.READY;
        this.errorMessage = null;
        this.nextRetryAt = null;
    }

    public void recordFailure(
            String message, Instant retryAt, int maxRetries) {
        this.retryCount++;
        this.status = DocumentStatus.FAILED;
        this.errorMessage = message;
        this.nextRetryAt =
                this.retryCount >= maxRetries ? null : retryAt;
    }

    public void markPermanentlyFailed(String message) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = message;
        this.nextRetryAt = null;
    }
}
