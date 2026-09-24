package com.altronixsoft.securerag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.DocumentStatus;
import com.altronixsoft.securerag.model.Entitlements;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DocumentRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final Entitlements ALICE = new Entitlements("sub-alice", "alice", Set.of("all-staff", "hr"), Set.of());
    private static final Entitlements BOB = new Entitlements("sub-bob", "bob", Set.of("all-staff", "engineering"), Set.of());

    @Autowired
    DocumentRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void storesAndReadsBackEveryColumn() {
        DocumentEntity saved = persist("sub-alice", Set.of("hr", "all-staff"), NOW);
        saved.markReady(4);
        entityManager.flush();
        entityManager.clear();

        DocumentEntity loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getOwnerSub()).isEqualTo("sub-alice");
        assertThat(loaded.getTitle()).isEqualTo("Salary policy");
        assertThat(loaded.getContentType()).isEqualTo("text/plain");
        assertThat(loaded.getSizeBytes()).isEqualTo(1024);
        assertThat(loaded.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(loaded.getChunkCount()).isEqualTo(4);
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getAllowedGroups()).containsExactlyInAnyOrder("hr", "all-staff");
    }

    @Test
    void ownerSeesOwnPrivateDocument() {
        DocumentEntity document = persist("sub-alice", Set.of(), NOW);

        assertThat(repository.findAllVisibleTo(ALICE)).containsExactly(document);
        assertThat(repository.findVisibleById(document.getId(), ALICE)).contains(document);
    }

    @Test
    void groupMemberSeesSharedDocument() {
        DocumentEntity document = persist("sub-alice", Set.of("all-staff"), NOW);

        assertThat(repository.findAllVisibleTo(BOB)).containsExactly(document);
        assertThat(repository.findVisibleById(document.getId(), BOB)).contains(document);
    }

    @Test
    void bobDoesNotSeeAlicesHrDocument() {
        DocumentEntity document = persist("sub-alice", Set.of("hr"), NOW);

        assertThat(repository.findAllVisibleTo(BOB)).isEmpty();
        assertThat(repository.findVisibleById(document.getId(), BOB)).isEmpty();
    }

    @Test
    void callerWithoutGroupsSeesOnlyOwnDocuments() {
        Entitlements aliceWithoutGroups = new Entitlements("sub-alice", "alice", Set.of(), Set.of());
        DocumentEntity own = persist("sub-alice", Set.of(), NOW);
        DocumentEntity shared = persist("sub-bob", Set.of("all-staff"), NOW);

        assertThat(repository.findAllVisibleTo(aliceWithoutGroups)).containsExactly(own);
        assertThat(repository.findVisibleById(shared.getId(), aliceWithoutGroups)).isEmpty();
    }

    @Test
    void callerWithoutSubjectSeesNothingEvenInSharedGroup() {
        Entitlements noSubject = new Entitlements(null, null, Set.of("all-staff"), Set.of());
        DocumentEntity document = persist("sub-alice", Set.of("all-staff"), NOW);

        assertThat(repository.findAllVisibleTo(noSubject)).isEmpty();
        assertThat(repository.findVisibleById(document.getId(), noSubject)).isEmpty();
    }

    @Test
    void documentSharedWithSeveralOfCallersGroupsAppearsOnce() {
        DocumentEntity document = persist("sub-bob", Set.of("all-staff", "hr"), NOW);

        assertThat(repository.findAllVisibleTo(ALICE)).containsExactly(document);
    }

    @Test
    void visibleDocumentsAreNewestFirst() {
        DocumentEntity older = persist("sub-alice", Set.of(), NOW);
        DocumentEntity newer = persist("sub-alice", Set.of(), NOW.plus(Duration.ofMinutes(1)));
        DocumentEntity sharedNewest = persist("sub-bob", Set.of("hr"), NOW.plus(Duration.ofMinutes(2)));

        assertThat(repository.findAllVisibleTo(ALICE)).containsExactly(sharedNewest, newer, older);
    }

    @Test
    void unknownIdIsIndistinguishableFromForeignDocument() {
        assertThat(repository.findVisibleById(UUID.randomUUID(), ALICE)).isEmpty();
    }

    @Test
    void deletingDocumentRowDeletesItsGroupsInTheDatabase() {
        DocumentEntity document = persist("sub-alice", Set.of("hr", "all-staff"), NOW);

        // Native delete: proves ON DELETE CASCADE in the schema, not Hibernate's own collection cleanup.
        entityManager.getEntityManager()
                .createNativeQuery("delete from documents where id = :id")
                .setParameter("id", document.getId())
                .executeUpdate();

        Number remainingGroups = (Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from document_groups where document_id = :id")
                .setParameter("id", document.getId())
                .getSingleResult();
        assertThat(remainingGroups.longValue()).isZero();
    }

    private DocumentEntity persist(String ownerSub, Set<String> groups, Instant createdAt) {
        DocumentEntity document = new DocumentEntity(
                UUID.randomUUID(), ownerSub, "Salary policy", "text/plain", 1024, groups, createdAt);
        entityManager.persistAndFlush(document);
        return document;
    }

}
