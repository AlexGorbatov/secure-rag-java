package com.altronixsoft.securerag.service.exception;

/**
 * Similarity search could not run: the embedding provider or the vector store is unreachable. The
 * cause keeps the original error for the log.
 */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(Throwable cause) {
        super("Similarity search failed", cause);
    }

}
