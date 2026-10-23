package com.axlero.ragassistant.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkMetadataTest {

    @Test
    void keysMatchTheAgreedContract() {
        assertEquals("documentId", ChunkMetadata.DOCUMENT_ID);
        assertEquals("ownerId", ChunkMetadata.OWNER_ID);
        assertEquals("filename", ChunkMetadata.FILENAME);
        assertEquals("page", ChunkMetadata.PAGE);
    }
}
