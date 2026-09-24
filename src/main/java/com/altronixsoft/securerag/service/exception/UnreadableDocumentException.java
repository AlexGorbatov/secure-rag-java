package com.altronixsoft.securerag.service.exception;

/**
 * The file has a supported type but yields no usable text: it is corrupted, encrypted, a scan
 * without a text layer, or empty. The message is for logs only.
 */
public class UnreadableDocumentException extends RuntimeException {

    public UnreadableDocumentException(String reason) {
        super(reason);
    }

    public UnreadableDocumentException(String reason, Throwable cause) {
        super(reason, cause);
    }

}
