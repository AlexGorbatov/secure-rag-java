package com.altronixsoft.securerag.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits and chunking for uploaded documents. Values live in {@code application.properties}; there
 * are no defaults here, so a missing value fails at startup instead of silently using a guess.
 *
 * @param chunkSizeTokens   target chunk size in tokens; small enough for the local ONNX model's input
 * @param maxTextCharacters extracted-text limit per document, enforced while parsing
 * @param maxTitleLength    longest title kept from the uploaded file name
 */
@Validated
@ConfigurationProperties("securerag.ingestion")
public record IngestionProperties(
        @Positive int chunkSizeTokens,
        @Positive int maxTextCharacters,
        @Positive int maxTitleLength) {
}
