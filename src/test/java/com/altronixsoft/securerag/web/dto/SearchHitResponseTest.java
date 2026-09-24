package com.altronixsoft.securerag.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.altronixsoft.securerag.service.ChunkMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class SearchHitResponseTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("7b1c3a52-0d6e-4f4b-9b0a-3c2f0f2b8e11");

    @Test
    void shortChunkIsReturnedWhole() {
        SearchHitResponse hit = SearchHitResponse.from(chunk("Paid leave is 25 days."));

        assertThat(hit.snippet()).isEqualTo("Paid leave is 25 days.");
        assertThat(hit.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(hit.title()).isEqualTo("policy.txt");
    }

    @Test
    void longChunkIsCutToSnippetLength() {
        SearchHitResponse hit = SearchHitResponse.from(chunk("a".repeat(SearchHitResponse.SNIPPET_LENGTH * 3)));

        assertThat(hit.snippet()).hasSize(SearchHitResponse.SNIPPET_LENGTH + 1).endsWith("…");
    }

    private static Document chunk(String text) {
        return Document.builder()
                .text(text)
                .metadata(Map.of(ChunkMetadata.DOCUMENT_ID, DOCUMENT_ID.toString(), ChunkMetadata.TITLE, "policy.txt"))
                .score(0.8)
                .build();
    }

}
