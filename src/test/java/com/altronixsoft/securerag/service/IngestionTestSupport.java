package com.altronixsoft.securerag.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.repository.DocumentRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Shared fixtures for ingestion tests: demo callers, a text long enough to produce several chunks,
 * and helpers to read back and remove what a test stored.
 */
public final class IngestionTestSupport {

    public static final Entitlements ALICE = new Entitlements("sub-alice", "alice", Set.of("all-staff", "hr"), Set.of());

    private static final int PARAGRAPHS = 40;

    private IngestionTestSupport() {
    }

    public static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    /** A policy text of several thousand words, so the splitter produces more than one chunk. */
    public static String longPolicyText() {
        return IntStream.rangeClosed(1, PARAGRAPHS)
                .mapToObj(i -> "Section " + i + ". Employees accrue paid leave every month; unused days of leave "
                        + "carry over to the next year up to a limit agreed with the manager and HR department.")
                .collect(Collectors.joining("\n\n"));
    }

    /** All chunks of one document, found by its id rather than by similarity. */
    public static List<Document> chunksOf(VectorStore vectorStore, UUID documentId) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("leave")
                .topK(1000)
                .similarityThresholdAll()
                .filterExpression(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, documentId.toString()).build())
                .build());
    }

    public static void remove(DocumentRepository repository, VectorStore vectorStore, List<UUID> documentIds) {
        for (UUID id : documentIds) {
            vectorStore.delete(new FilterExpressionBuilder().eq(ChunkMetadata.DOCUMENT_ID, id.toString()).build());
            repository.deleteById(id);
        }
    }

}
