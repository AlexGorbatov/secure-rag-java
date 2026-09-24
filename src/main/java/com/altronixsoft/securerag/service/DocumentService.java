package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.exception.DocumentNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Short transactions around the documents table. Kept separate from {@link DocumentIngestionService}
 * so each call goes through Spring's transactional proxy, and so no transaction is open while
 * embeddings are computed.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final Clock clock;

    DocumentService(DocumentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public DocumentEntity createProcessing(UUID id, ParsedDocument parsed, String ownerSub, Set<String> allowedGroups) {
        return repository.save(new DocumentEntity(
                id, ownerSub, parsed.title(), parsed.contentType(), parsed.sizeBytes(), allowedGroups,
                Instant.now(clock)));
    }

    @Transactional
    public DocumentEntity markReady(UUID id, int chunkCount) {
        DocumentEntity document = load(id);
        document.markReady(chunkCount);
        return document;
    }

    @Transactional
    public void markFailed(UUID id) {
        load(id).markFailed();
    }

    private DocumentEntity load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

}
