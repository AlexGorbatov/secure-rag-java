package com.altronixsoft.securerag.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An uploaded document and the source of truth for who may read it: the owner ({@code sub} from the
 * token) plus the groups it is shared with. Chunks in the vector store carry a copy of this ACL.
 *
 * <p>Visibility is decided only by the repository queries, never re-implemented here, so the rule
 * lives in one place.
 *
 * <p>Created as {@link DocumentStatus#PROCESSING}; ingestion then moves it exactly once to
 * {@link DocumentStatus#READY} or {@link DocumentStatus#FAILED}.
 */
@Getter
@Entity
@Table(name = "documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for Hibernate
public class DocumentEntity {

    private static final int NO_CHUNKS = 0;

    @Id
    private UUID id;

    @Column(name = "owner_sub", nullable = false, updatable = false)
    private String ownerSub;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // EAGER: a document has a handful of groups, and open-in-view is off, so a lazy collection would
    // throw LazyInitializationException when a controller maps the entity after the transaction ends.
    @Getter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "document_groups", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "group_name", nullable = false)
    private Set<String> allowedGroups = new HashSet<>();

    public DocumentEntity(UUID id, String ownerSub, String title, String contentType,
                          long sizeBytes, Set<String> allowedGroups, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerSub = requireText(ownerSub, "ownerSub");
        this.title = requireText(title, "title");
        this.contentType = requireText(contentType, "contentType");
        this.sizeBytes = requireNotNegative(sizeBytes, "sizeBytes");
        this.allowedGroups = copyOfGroups(allowedGroups);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = DocumentStatus.PROCESSING;
        this.chunkCount = NO_CHUNKS;
    }

    public void markReady(int chunkCount) {
        requireNotNegative(chunkCount, "chunkCount");
        finishProcessing(DocumentStatus.READY, chunkCount);
    }

    public void markFailed() {
        finishProcessing(DocumentStatus.FAILED, NO_CHUNKS);
    }

    public Set<String> getAllowedGroups() {
        return Collections.unmodifiableSet(allowedGroups);
    }

    private void finishProcessing(DocumentStatus result, int chunks) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Document " + id + " is already " + status);
        }
        this.status = result;
        this.chunkCount = chunks;
    }

    // Identity is the id: assigned in the constructor, never null, never changed. Both methods go
    // through getId() rather than the field, and are not final, because a Hibernate proxy is a subclass
    // whose own fields are empty; getId() on a proxy returns the identifier without loading it.
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DocumentEntity document && getId().equals(document.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    // No title and no groups: an entity in a log line must not leak document metadata.
    @Override
    public String toString() {
        return "DocumentEntity[id=" + getId() + ", status=" + status + "]";
    }

    private static Set<String> copyOfGroups(Set<String> groups) {
        Objects.requireNonNull(groups, "allowedGroups");
        groups.forEach(group -> requireText(group, "allowedGroups element"));
        return new HashSet<>(groups);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static long requireNotNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

}
