package com.altronixsoft.securerag.service.exception;

import lombok.Getter;

/**
 * The extracted text exceeds the configured limit. Checked while parsing, so a small file that
 * expands into a huge amount of text (a compression bomb) is stopped before it fills memory.
 */
@Getter
public class DocumentTooLargeException extends RuntimeException {

    private final int maxTextCharacters;

    public DocumentTooLargeException(int maxTextCharacters, Throwable cause) {
        super("Extracted text exceeds " + maxTextCharacters + " characters", cause);
        this.maxTextCharacters = maxTextCharacters;
    }

}
