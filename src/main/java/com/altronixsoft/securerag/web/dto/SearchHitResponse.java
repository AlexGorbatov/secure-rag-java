package com.altronixsoft.securerag.web.dto;

import com.altronixsoft.securerag.service.ChunkMetadata;
import org.springframework.ai.document.Document;

import java.util.UUID;

/**
 * One matching chunk: which document it belongs to, a short excerpt and how similar it is (0..1).
 */
public record SearchHitResponse(UUID documentId, String title, String snippet, Double score) {

    static final int SNIPPET_LENGTH = 300;
    private static final String ELLIPSIS = "…";

    public static SearchHitResponse from(Document chunk) {
        return new SearchHitResponse(
                UUID.fromString((String) chunk.getMetadata().get(ChunkMetadata.DOCUMENT_ID)),
                (String) chunk.getMetadata().get(ChunkMetadata.TITLE),
                snippetOf(chunk.getText()),
                chunk.getScore());
    }

    private static String snippetOf(String text) {
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + ELLIPSIS;
    }

}
