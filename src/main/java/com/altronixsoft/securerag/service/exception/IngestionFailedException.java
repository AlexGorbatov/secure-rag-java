package com.altronixsoft.securerag.service.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Parsing, chunking or embedding a document failed after its row was created. The document is marked
 * FAILED and its chunks are removed; {@code cause} keeps the original error for the log.
 */
@Getter
public class IngestionFailedException extends RuntimeException {

    private final UUID documentId;

    public IngestionFailedException(UUID documentId, Throwable cause) {
        super("Ingestion of document " + documentId + " failed", cause);
        this.documentId = documentId;
    }

}
