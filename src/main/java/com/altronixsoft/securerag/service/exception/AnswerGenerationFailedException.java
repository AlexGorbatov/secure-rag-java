package com.altronixsoft.securerag.service.exception;

/**
 * The chat model could not produce an answer: it was unreachable, timed out, rejected the request or
 * returned nothing. The cause keeps the provider's error for the log.
 */
public class AnswerGenerationFailedException extends RuntimeException {

    public AnswerGenerationFailedException(String reason, Throwable cause) {
        super(reason, cause);
    }

}
