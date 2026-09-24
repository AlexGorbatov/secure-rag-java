package com.altronixsoft.securerag.service;

import static com.altronixsoft.securerag.service.IngestionTestSupport.ALICE;
import static com.altronixsoft.securerag.service.IngestionTestSupport.chunksOf;
import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.exception.DocumentNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentServiceTest {

    @Autowired
    DocumentService documentService;

    @Autowired
    DocumentIngestionService ingestionService;

    @Autowired
    DocumentRepository repository;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    TransactionTemplate transactionTemplate;

    private UUID document;

    @BeforeEach
    void aliceUploadsADocument() {
        document = ingestionService.ingest(textFile("policy.txt", "Paid leave is 25 days."), Set.of(), ALICE).getId();
    }

    @AfterEach
    void removeDocument() {
        IngestionTestSupport.remove(repository, vectorStore, List.of(document));
    }

    /**
     * delete() relies on this: the vector store writes through the same DataSource, so its delete
     * joins the surrounding transaction and rolls back with it. If it did not, a failure after the
     * chunk delete would leave a document without chunks, or the reverse.
     */
    @Test
    void chunkDeleteJoinsTheSurroundingTransaction() {
        transactionTemplate.executeWithoutResult(status -> {
            vectorStore.delete(ChunkMetadata.belongsTo(document));
            status.setRollbackOnly();
        });

        assertThat(chunksOf(vectorStore, document)).isNotEmpty();
    }

    @Test
    void callerWithoutSubjectCannotDelete() {
        Entitlements noSubject = new Entitlements(null, null, Set.of("all-staff", "hr"), Set.of());

        assertThatThrownBy(() -> documentService.delete(document, noSubject))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThat(repository.findById(document)).isPresent();
    }

}
