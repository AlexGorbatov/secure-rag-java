package com.altronixsoft.securerag.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A search request. Named {@code SearchQuery} to avoid clashing with Spring AI's {@code SearchRequest}.
 * It carries only the text and the number of results: who may see what comes from the token, never
 * from here.
 *
 * @param query text to search for
 * @param topK  number of chunks to return; the configured default when omitted
 */
public record SearchQuery(
        @NotBlank @Size(max = MAX_QUERY_LENGTH) String query,
        @Min(1) @Max(MAX_TOP_K) Integer topK) {

    public static final int MAX_QUERY_LENGTH = 2000;
    public static final int MAX_TOP_K = 20;

}
