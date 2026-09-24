package com.altronixsoft.securerag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.Entitlements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The core guarantee of the project: similarity search never returns a chunk the caller may not read.
 *
 * <p>Alice's and Bob's texts are deliberately about the same topic (salaries), so without the access
 * filter a search by Bob would surface Alice's chunk. {@link #withoutFilterBobsQueryWouldFindAlicesChunk}
 * proves that, so the other tests pass because of the filter and not because the texts are unrelated.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RetrievalServiceTest {

    private static final String ALICE_HR_TEXT = "Salary bands for 2026: engineers earn between two agreed limits.";
    private static final String BOB_TEXT = "Salary review process for the engineering team happens every spring.";
    private static final String QUERY = "what are the salaries?";
    private static final int TOP_K = 10;

    private static final Entitlements ALICE = new Entitlements("sub-alice", "alice", Set.of("all-staff", "hr"), Set.of());
    private static final Entitlements BOB = new Entitlements("sub-bob", "bob", Set.of("all-staff", "engineering"), Set.of());

    @Autowired
    RetrievalService retrievalService;

    @Autowired
    VectorStore vectorStore;

    private final List<String> addedChunks = new ArrayList<>();

    @AfterEach
    void removeChunks() {
        if (!addedChunks.isEmpty()) {
            vectorStore.delete(addedChunks);
        }
    }

    @Test
    void withoutFilterBobsQueryWouldFindAlicesChunk() {
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("hr"));
        addChunk(BOB_TEXT, "sub-bob", List.of("engineering"));

        List<Document> unfiltered = vectorStore.similaritySearch(
                SearchRequest.builder().query(QUERY).topK(TOP_K).similarityThresholdAll().build());

        assertThat(texts(unfiltered)).contains(ALICE_HR_TEXT, BOB_TEXT);
    }

    @Test
    void bobCannotRetrieveChunksOfAlicesHrDocument() {
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("hr"));
        addChunk(BOB_TEXT, "sub-bob", List.of("engineering"));

        assertThat(texts(retrievalService.findRelevant(QUERY, TOP_K, BOB)))
                .contains(BOB_TEXT)
                .doesNotContain(ALICE_HR_TEXT);
    }

    @Test
    void ownerRetrievesPrivateChunk() {
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of());

        assertThat(texts(retrievalService.findRelevant(QUERY, TOP_K, ALICE))).contains(ALICE_HR_TEXT);
    }

    @Test
    void groupMemberRetrievesChunkSharedWithOneOfSeveralGroups() {
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("hr", "all-staff"));

        assertThat(texts(retrievalService.findRelevant(QUERY, TOP_K, BOB))).contains(ALICE_HR_TEXT);
    }

    @Test
    void callerWithoutGroupsRetrievesOnlyOwnChunks() {
        Entitlements bobWithoutGroups = new Entitlements("sub-bob", "bob", Set.of(), Set.of());
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("all-staff"));
        addChunk(BOB_TEXT, "sub-bob", List.of());

        assertThat(texts(retrievalService.findRelevant(QUERY, TOP_K, bobWithoutGroups)))
                .containsExactly(BOB_TEXT);
    }

    @Test
    void callerWithoutSubjectRetrievesNothingEvenFromSharedChunks() {
        Entitlements noSubject = new Entitlements(null, null, Set.of("all-staff"), Set.of());
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("all-staff"));

        assertThat(retrievalService.findRelevant(QUERY, TOP_K, noSubject)).isEmpty();
    }

    @Test
    void chunkWithoutAccessMetadataIsInvisibleToEveryone() {
        addChunk(ALICE_HR_TEXT, null, null);

        assertThat(texts(retrievalService.findRelevant(QUERY, TOP_K, ALICE))).doesNotContain(ALICE_HR_TEXT);
        assertThat(texts(retrievalService.findRelevant(QUERY, TOP_K, BOB))).doesNotContain(ALICE_HR_TEXT);
    }

    @Test
    void jsonPathInjectionInSubjectDoesNotWidenAccess() {
        Entitlements attacker = new Entitlements("x\" || $.owner != \"x", "mallory", Set.of(), Set.of());
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("hr"));

        assertThat(retrievalService.findRelevant(QUERY, TOP_K, attacker)).isEmpty();
    }

    @Test
    void jsonPathInjectionInGroupDoesNotWidenAccess() {
        Entitlements attacker = new Entitlements("sub-mallory", "mallory", Set.of("x\" || $.owner != \"x"), Set.of());
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of("hr"));

        assertThat(retrievalService.findRelevant(QUERY, TOP_K, attacker)).isEmpty();
    }

    @Test
    void returnsAtMostTopKChunks() {
        addChunk(ALICE_HR_TEXT, "sub-alice", List.of());
        addChunk("Salary increases are reviewed yearly by HR.", "sub-alice", List.of());
        addChunk("Salaries are paid on the last working day.", "sub-alice", List.of());

        assertThat(retrievalService.findRelevant(QUERY, 2, ALICE)).hasSize(2);
    }

    private void addChunk(String text, String owner, List<String> allowedGroups) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(ChunkMetadata.DOCUMENT_ID, UUID.randomUUID().toString());
        if (owner != null) {
            metadata.put(ChunkMetadata.OWNER, owner);
        }
        if (allowedGroups != null) {
            metadata.put(ChunkMetadata.ALLOWED_GROUPS, allowedGroups);
        }
        Document chunk = Document.builder().id(UUID.randomUUID().toString()).text(text).metadata(metadata).build();
        vectorStore.add(List.of(chunk));
        addedChunks.add(chunk.getId());
    }

    private static List<String> texts(List<Document> documents) {
        return documents.stream().map(Document::getText).toList();
    }

}
