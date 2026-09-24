package com.altronixsoft.securerag.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DocumentEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void newDocumentIsProcessingWithNoChunks() {
        DocumentEntity document = aliceDocumentSharedWith(Set.of("hr"));

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.getChunkCount()).isZero();
        assertThat(document.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void markReadyRecordsChunkCount() {
        DocumentEntity document = aliceDocumentSharedWith(Set.of());

        document.markReady(7);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isEqualTo(7);
    }

    @Test
    void markFailedClearsChunkCount() {
        DocumentEntity document = aliceDocumentSharedWith(Set.of());

        document.markFailed();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getChunkCount()).isZero();
    }

    @Test
    void statusCannotChangeOnceReady() {
        DocumentEntity document = aliceDocumentSharedWith(Set.of());
        document.markReady(3);

        assertThatThrownBy(document::markFailed).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> document.markReady(5)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowedGroupsAreCopiedAndReadOnly() {
        Set<String> groups = new HashSet<>(Set.of("hr"));
        DocumentEntity document = aliceDocumentSharedWith(groups);

        groups.add("engineering");

        assertThat(document.getAllowedGroups()).containsExactly("hr");
        assertThatThrownBy(() -> document.getAllowedGroups().add("engineering"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNegativeChunkCount() {
        DocumentEntity document = aliceDocumentSharedWith(Set.of());

        assertThatThrownBy(() -> document.markReady(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void rejectsBlankGroupName() {
        assertThatThrownBy(() -> aliceDocumentSharedWith(Set.of(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void documentsWithSameIdAreEqual() {
        UUID id = UUID.randomUUID();
        DocumentEntity first = new DocumentEntity(id, "sub-alice", "a", "text/plain", 1, Set.of(), CREATED_AT);
        DocumentEntity second = new DocumentEntity(id, "sub-alice", "b", "text/plain", 2, Set.of("hr"), CREATED_AT);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(aliceDocumentSharedWith(Set.of()));
    }

    @Test
    void toStringDoesNotLeakTitleOrGroups() {
        assertThat(aliceDocumentSharedWith(Set.of("hr")).toString())
                .doesNotContain("Salary policy")
                .doesNotContain("hr");
    }

    @Test
    void rejectsBlankOwner() {
        assertThatThrownBy(() -> new DocumentEntity(UUID.randomUUID(), " ", "t", "text/plain", 1, Set.of(), CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DocumentEntity aliceDocumentSharedWith(Set<String> groups) {
        return new DocumentEntity(UUID.randomUUID(), "sub-alice", "Salary policy", "text/plain", 1024, groups, CREATED_AT);
    }

}
