package com.altronixsoft.securerag.web.dto;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.DocumentStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int chunkCount,
        Set<String> allowedGroups,
        Instant createdAt) {

    public static DocumentResponse from(DocumentEntity document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getChunkCount(),
                Set.copyOf(document.getAllowedGroups()),
                document.getCreatedAt());
    }

}
