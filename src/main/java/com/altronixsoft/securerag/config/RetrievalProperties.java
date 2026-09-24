package com.altronixsoft.securerag.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Similarity search settings. Values live in {@code application.properties}.
 *
 * @param defaultTopK         chunks returned when the caller does not ask for a number
 * @param similarityThreshold minimum cosine similarity (0..1) for a chunk to count as relevant
 */
@Validated
@ConfigurationProperties("securerag.retrieval")
public record RetrievalProperties(
        @Positive int defaultTopK,
        @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold) {
}
