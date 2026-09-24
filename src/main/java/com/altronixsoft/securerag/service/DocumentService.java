package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.exception.DocumentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Short transactions that change documents. Kept separate from {@link DocumentIngestionService} so
 * each call goes through Spring's transactional proxy, and so no transaction is open while embeddings
 * are computed.
 */
@Slf4j
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final VectorStore vectorStore;
    private final Clock clock;

    DocumentService(DocumentRepository repository, VectorStore vectorStore, Clock clock) {
        this.repository = repository;
        this.vectorStore = vectorStore;
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

    /**
     * Deletes a document and all its chunks. Only the owner may delete: for anyone else, including a
     * member of a group it is shared with, the document is "not found".
     *
     * <p>The vector store writes through the same DataSource, so the chunk delete joins this
     * transaction: either both the chunks and the row go, or neither does. No orphaned chunk can stay
     * searchable.
     */
    @Transactional
    public void delete(UUID id, Entitlements caller) {
        DocumentEntity document = findOwned(id, caller);
        vectorStore.delete(ChunkMetadata.belongsTo(id));
        repository.delete(document);
        log.info("Deleted document {}", id);
    }

    private DocumentEntity findOwned(UUID id, Entitlements caller) {
        if (caller.subject() == null) {
            throw new DocumentNotFoundException(id);
        }
        return repository.findByIdAndOwnerSub(id, caller.subject())
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    private DocumentEntity load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
    }

}
