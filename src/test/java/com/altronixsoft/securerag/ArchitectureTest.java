package com.altronixsoft.securerag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * VectorStore.similaritySearch can return chunks the caller may not be entitled to unless every call
 * goes through RetrievalService's ACL filter. This scans the actual source tree — the cheapest layer
 * that proves the claim — rather than adding an ArchUnit dependency for one rule.
 *
 * <p>Writes and deletes to VectorStore from DocumentIngestionService/DocumentService are fine: they
 * never return chunk content to a caller, so they carry no leak risk and are out of scope here.
 */
class ArchitectureTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path RETRIEVAL_SERVICE =
            MAIN_SOURCE_ROOT.resolve("com/altronixsoft/securerag/service/RetrievalService.java");
    private static final String SEARCH_CALL = ".similaritySearch(";

    @Test
    void vectorStoreSimilaritySearchIsCalledOnlyFromRetrievalService() throws IOException {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(RETRIEVAL_SERVICE))
                    .filter(ArchitectureTest::containsSearchCall)
                    .toList();
        }
        assertThat(offenders)
                .as("only RetrievalService may call VectorStore.similaritySearch")
                .isEmpty();
    }

    /** Guards against the test above passing vacuously if RetrievalService stops calling it at all. */
    @Test
    void retrievalServiceIsTheOneCallerOfSimilaritySearch() throws IOException {
        assertThat(Files.readString(RETRIEVAL_SERVICE)).contains(SEARCH_CALL);
    }

    private static boolean containsSearchCall(Path file) {
        try {
            return Files.readString(file).contains(SEARCH_CALL);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
