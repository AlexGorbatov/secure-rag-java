package com.altronixsoft.securerag.startup;

import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.DocumentIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Loads a small demo document set on a free local run so there is something to ask questions about
 * immediately.
 *
 * <p>Gated on the "demo" profile, not "test": {@code TestSecureRagJavaApplication} (the
 * {@code spring-boot:test-run} entry point) and the whole automated test suite both activate the
 * "test" profile, so gating on "test" alone would seed 7 documents into the shared Testcontainers
 * database before every integration test runs. Only {@code TestSecureRagJavaApplication} additionally
 * activates "demo"; {@code ./mvnw test`/`verify} never does.
 */
@Slf4j
@Component
@Profile("demo")
class SeedDataRunner implements ApplicationRunner {

    static final String SEED_OWNER = "seed-admin";

    /** The seed admin must belong to every group used in the manifest, or sharing with it is rejected. */
    private static final Set<String> SEED_OWNER_GROUPS = Set.of("all-staff", "hr", "finance", "engineering");

    private static final String SEED_DIR = "seed-docs/";
    private static final String MANIFEST = SEED_DIR + "manifest.json";

    private final DocumentRepository documents;
    private final DocumentIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    SeedDataRunner(DocumentRepository documents, DocumentIngestionService ingestionService, ObjectMapper objectMapper) {
        this.documents = documents;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (documents.count() > 0) {
            log.info("Documents already present, skipping seed data");
            return;
        }
        List<SeedManifestEntry> manifest = readManifest();
        Entitlements owner = new Entitlements(SEED_OWNER, SEED_OWNER, SEED_OWNER_GROUPS, Set.of());
        for (SeedManifestEntry entry : manifest) {
            ingestionService.ingest(seedFile(entry.file()), Set.copyOf(entry.groups()), owner);
        }
        log.info("Seeded {} demo documents", manifest.size());
    }

    private List<SeedManifestEntry> readManifest() throws IOException {
        try (InputStream in = new ClassPathResource(MANIFEST).getInputStream()) {
            return objectMapper.readerForListOf(SeedManifestEntry.class).readValue(in);
        }
    }

    private ClasspathSeedFile seedFile(String filename) throws IOException {
        try (InputStream in = new ClassPathResource(SEED_DIR + filename).getInputStream()) {
            return new ClasspathSeedFile(filename, in.readAllBytes());
        }
    }

}
