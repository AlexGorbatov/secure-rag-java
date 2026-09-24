package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.exception.GroupNotAllowedException;
import com.altronixsoft.securerag.service.exception.IngestionFailedException;
import com.altronixsoft.securerag.service.exception.UnreadableDocumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Upload → validate → extract text → chunk with ACL metadata → embed and store.
 *
 * <p>Deliberately not {@code @Transactional}: embedding is a network call that can take seconds, so the
 * database is touched only in the short transactions of {@link DocumentService}. Everything that can
 * reject the upload runs before the first write, so a rejected file leaves nothing behind; a failure
 * after the row exists leaves the document FAILED with no chunks.
 */
@Slf4j
@Service
public class DocumentIngestionService {

    private final DocumentParser parser;
    private final DocumentService documents;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;

    DocumentIngestionService(DocumentParser parser, DocumentService documents,
                             TokenTextSplitter splitter, VectorStore vectorStore) {
        this.parser = parser;
        this.documents = documents;
        this.splitter = splitter;
        this.vectorStore = vectorStore;
    }

    public DocumentEntity ingest(MultipartFile file, Set<String> groups, Entitlements caller) {
        requireOwner(caller);
        requireMembership(groups, caller);

        ParsedDocument parsed = parser.parse(file);
        UUID id = UUID.randomUUID();
        List<Document> chunks = chunk(id, parsed, groups, caller);

        documents.createProcessing(id, parsed, caller.subject(), groups);
        try {
            vectorStore.add(chunks);
            DocumentEntity ready = documents.markReady(id, chunks.size());
            log.info("Ingested document {} ({}, {} bytes) into {} chunks",
                    id, parsed.contentType(), parsed.sizeBytes(), chunks.size());
            return ready;
        } catch (RuntimeException e) {
            cleanUpAfterFailure(id, e);
            throw new IngestionFailedException(id, e);
        }
    }

    /** Ownership comes from the token's subject; without one there is nobody to own the document. */
    private static void requireOwner(Entitlements caller) {
        if (caller.subject() == null) {
            throw new AccessDeniedException("Token has no subject");
        }
    }

    /** A document can only be shared with groups the uploader belongs to. */
    private static void requireMembership(Set<String> groups, Entitlements caller) {
        groups.stream()
                .filter(group -> !caller.groups().contains(group))
                .findFirst()
                .ifPresent(group -> {
                    throw new GroupNotAllowedException(group);
                });
    }

    /**
     * Every chunk carries the document's ACL, copied from what is about to be stored in the documents
     * table. The splitter copies the source metadata into each chunk.
     */
    private List<Document> chunk(UUID id, ParsedDocument parsed, Set<String> groups, Entitlements caller) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ChunkMetadata.DOCUMENT_ID, id.toString());
        metadata.put(ChunkMetadata.OWNER, caller.subject());
        metadata.put(ChunkMetadata.ALLOWED_GROUPS, List.copyOf(groups));
        metadata.put(ChunkMetadata.TITLE, parsed.title());

        List<Document> chunks = splitter.apply(List.of(new Document(parsed.text(), metadata)));
        if (chunks.isEmpty()) {
            throw new UnreadableDocumentException("Text produced no chunks");
        }
        return chunks;
    }

    /**
     * Best effort: remove any chunks that did get stored and mark the row FAILED. A failure here is
     * attached to the original error rather than hiding it.
     */
    private void cleanUpAfterFailure(UUID id, RuntimeException failure) {
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, id.toString()).build());
            documents.markFailed(id);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

}
