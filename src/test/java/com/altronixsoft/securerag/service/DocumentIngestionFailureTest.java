package com.altronixsoft.securerag.service;

import static com.altronixsoft.securerag.service.IngestionTestSupport.ALICE;
import static com.altronixsoft.securerag.service.IngestionTestSupport.chunksOf;
import static com.altronixsoft.securerag.service.IngestionTestSupport.longPolicyText;
import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Set;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.DocumentStatus;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.exception.IngestionFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Separate class because the spy changes the application context; keeping it out of
 * DocumentIngestionServiceTest lets that class share the cached context with other tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentIngestionFailureTest {

    @Autowired
    DocumentIngestionService ingestionService;

    @Autowired
    DocumentRepository repository;

    @MockitoSpyBean
    VectorStore vectorStore;

    @Test
    void embeddingFailureLeavesFailedDocumentAndNoChunks() {
        doThrow(new IllegalStateException("embedding service unavailable")).when(vectorStore).add(anyList());

        IngestionFailedException failure = catchThrowableOfType(IngestionFailedException.class,
                () -> ingestionService.ingest(textFile("policy.txt", longPolicyText()), Set.of("hr"), ALICE));

        assertThat(failure.getCause()).hasMessage("embedding service unavailable");
        assertThat(repository.findById(failure.getDocumentId())).get()
                .satisfies(document -> {
                    assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(document.getChunkCount()).isZero();
                });
        assertThat(chunksOf(vectorStore, failure.getDocumentId())).isEmpty();

        IngestionTestSupport.remove(repository, vectorStore, List.of(failure.getDocumentId()));
    }

}
