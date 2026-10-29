package com.axlero.ragassistant.repositories;

import com.axlero.ragassistant.entities.DocumentIngestionChunk;
import com.axlero.ragassistant.entities.DocumentIngestionChunk.ChunkId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIngestionChunkRepository
        extends JpaRepository<DocumentIngestionChunk, ChunkId> {

    List<DocumentIngestionChunk> findAllByDocumentIdOrderByChunkIndex(
            UUID documentId);

    void deleteAllByDocumentId(UUID documentId);
}
