package com.altronixsoft.securerag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The vector store works end to end against real pgvector with the local ONNX embedding model:
 * the table created by Flyway accepts the model's vectors, and similarity search finds by meaning.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VectorStoreSmokeTest {

    @Autowired
    VectorStore vectorStore;

    @Autowired
    EmbeddingModel embeddingModel;

    @Value("${spring.ai.vectorstore.pgvector.dimensions}")
    int configuredDimensions;

    private final List<String> addedIds = new ArrayList<>();

    @AfterEach
    void removeAddedChunks() {
        if (!addedIds.isEmpty()) {
            vectorStore.delete(addedIds);
        }
    }

    @Test
    void embeddingModelOutputMatchesVectorColumnDimension() {
        assertThat(embeddingModel.dimensions()).isEqualTo(configuredDimensions);
    }

    @Test
    void findsSemanticallyClosestChunk() {
        add("Vacation policy: employees get 25 days of paid leave per year.", Map.of("document_id", "vacation"));
        add("The build pipeline runs unit tests on every commit.", Map.of("document_id", "pipeline"));

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query("how many holidays do I have?").topK(1).build());

        assertThat(hits).singleElement()
                .satisfies(hit -> assertThat(hit.getMetadata()).containsEntry("document_id", "vacation"));
    }

    @Test
    void metadataRoundTripsIncludingGroupList() {
        add("Office opening hours are 9 to 18 on weekdays.",
                Map.of("document_id", "office", "owner", "sub-alice", "allowed_groups", List.of("all-staff", "hr")));

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query("when is the office open?").topK(1).build());

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.getMetadata()).containsEntry("owner", "sub-alice");
            assertThat(hit.getMetadata().get("allowed_groups")).isEqualTo(List.of("all-staff", "hr"));
        });
    }

    private void add(String text, Map<String, Object> metadata) {
        Document document = Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(metadata)
                .build();
        vectorStore.add(List.of(document));
        addedIds.add(document.getId());
    }

}
