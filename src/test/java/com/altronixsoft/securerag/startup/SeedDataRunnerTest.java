package com.altronixsoft.securerag.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.IngestionTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code @TestInstance(PER_CLASS)} + a non-static {@code @AfterAll} rather than {@code @AfterEach}:
 * SeedDataRunner is an ApplicationRunner, so it seeds exactly once when this class's Spring context is
 * first created, not before every {@code @Test} method. Cleaning up per-method would delete the seeded
 * rows before the second method runs, since the (cached) context never seeds twice. Cleaning up once,
 * after both methods, keeps the shared Testcontainers database clean for every other test class.
 */
@SpringBootTest
@ActiveProfiles({"test", "demo"})
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeedDataRunnerTest {

    @Autowired
    DocumentRepository repository;

    @Autowired
    SeedDataRunner runner;

    @Autowired
    VectorStore vectorStore;

    @AfterAll
    void removeSeedDocuments() {
        List<UUID> seededIds = repository.findAll().stream()
                .filter(document -> document.getOwnerSub().equals(SeedDataRunner.SEED_OWNER))
                .map(DocumentEntity::getId)
                .toList();
        IngestionTestSupport.remove(repository, vectorStore, seededIds);
    }

    @Test
    void seedsSevenDocumentsWithExpectedGroupsOnAnEmptyDatabase() {
        List<DocumentEntity> seeded = repository.findAll();

        assertThat(seeded).hasSize(7);
        assertThat(seeded).extracting(DocumentEntity::getOwnerSub).containsOnly(SeedDataRunner.SEED_OWNER);
        assertThat(seeded)
                .filteredOn(document -> document.getTitle().equals("salary-bands-2026.md"))
                .extracting(DocumentEntity::getAllowedGroups)
                .containsExactly(Set.of("hr"));
    }

    @Test
    void runningItAgainWhenDocumentsExistDoesNothing() throws Exception {
        int before = repository.findAll().size();

        runner.run(null);

        assertThat(repository.findAll()).hasSize(before);
    }

}
