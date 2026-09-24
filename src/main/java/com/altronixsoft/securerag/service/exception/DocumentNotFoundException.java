package com.altronixsoft.securerag.service.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * The document does not exist, <b>or</b> the caller is not allowed to see it. Both cases are
 * deliberately the same exception, so the response (404) never reveals that someone else's document
 * exists.
 *
 * <p>The message is for logs only; the HTTP response uses its own caller-safe text.
 */
@Getter
public class DocumentNotFoundException extends RuntimeException {

    private final UUID documentId;

    public DocumentNotFoundException(UUID documentId) {
        super("Document " + documentId + " not found or not visible to the caller");
        this.documentId = documentId;
    }

}
