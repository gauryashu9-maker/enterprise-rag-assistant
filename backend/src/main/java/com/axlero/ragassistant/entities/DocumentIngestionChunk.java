package com.axlero.ragassistant.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "document_ingestion_chunks")
@IdClass(DocumentIngestionChunk.ChunkId.class)
public class DocumentIngestionChunk {

    @Id
    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Id
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected DocumentIngestionChunk() {}

    public DocumentIngestionChunk(UUID documentId, int chunkIndex, String content) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    public UUID getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }

    public static class ChunkId implements Serializable {
        private UUID documentId;
        private int chunkIndex;

        public ChunkId() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ChunkId that)) return false;
            return chunkIndex == that.chunkIndex
                    && Objects.equals(documentId, that.documentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(documentId, chunkIndex);
        }
    }
}
