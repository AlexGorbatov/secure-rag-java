package com.altronixsoft.securerag.service;

/**
 * Metadata keys every chunk in the vector store carries. Ingestion writes them and retrieval filters
 * on them, so both sides use these constants rather than string literals.
 */
public final class ChunkMetadata {

    public static final String DOCUMENT_ID = "document_id";
    public static final String OWNER = "owner";
    public static final String ALLOWED_GROUPS = "allowed_groups";
    public static final String TITLE = "title";

    private ChunkMetadata() {
    }

}
