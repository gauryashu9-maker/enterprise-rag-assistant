package com.axlero.ragassistant.services;

/**
 * Metadata keys stored on every vector chunk. Ingestion writes them; retrieval, citations and
 * document deletion read or filter on them. Keep the values stable: other modules depend on them.
 */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "documentId";
    public static final String OWNER_ID = "ownerId";
    public static final String FILENAME = "filename";
    public static final String PAGE = "page";

    private ChunkMetadata() {
    }
}
