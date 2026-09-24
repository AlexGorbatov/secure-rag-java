package com.altronixsoft.securerag.service;

import static com.altronixsoft.securerag.service.IngestionTestSupport.ALICE;
import static com.altronixsoft.securerag.service.IngestionTestSupport.chunksOf;
import static com.altronixsoft.securerag.service.IngestionTestSupport.longPolicyText;
import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.DocumentEntity;
import com.altronixsoft.securerag.model.DocumentStatus;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.exception.GroupNotAllowedException;
import com.altronixsoft.securerag.service.exception.UnsupportedDocumentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentIngestionServiceTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

    @Autowired
    DocumentIngestionService ingestionService;

    @Autowired
    DocumentRepository repository;

    @Autowired
    VectorStore vectorStore;

    private final List<UUID> createdDocuments = new ArrayList<>();

    @AfterEach
    void removeCreatedDocuments() {
        IngestionTestSupport.remove(repository, vectorStore, createdDocuments);
    }

    @Test
    void ingestsTextAndStoresEveryChunkWithTheDocumentsAcl() {
        DocumentEntity document = ingest(textFile("leave-policy.txt", longPolicyText()), Set.of("all-staff"), ALICE);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isGreaterThan(1);
        assertThat(repository.findById(document.getId())).get()
                .extracting(DocumentEntity::getStatus).isEqualTo(DocumentStatus.READY);

        List<Document> chunks = chunksOf(vectorStore, document.getId());
        assertThat(chunks).hasSize(document.getChunkCount());
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.OWNER, "sub-alice");
            assertThat(chunk.getMetadata()).containsEntry(ChunkMetadata.TITLE, "leave-policy.txt");
            assertThat(chunk.getMetadata().get(ChunkMetadata.ALLOWED_GROUPS)).isEqualTo(List.of("all-staff"));
        });
    }

    @Test
    void privateDocumentChunksCarryAnEmptyGroupList() {
        DocumentEntity document = ingest(textFile("private.txt", "Personal notes about my leave."), Set.of(), ALICE);

        assertThat(chunksOf(vectorStore, document.getId())).allSatisfy(chunk ->
                assertThat(chunk.getMetadata().get(ChunkMetadata.ALLOWED_GROUPS)).isEqualTo(List.of()));
    }

    @Test
    void rejectsSharingWithForeignGroupBeforeStoringAnything() {
        long before = repository.count();

        assertThatThrownBy(() -> ingest(textFile("x.txt", "text"), Set.of("engineering"), ALICE))
                .isInstanceOf(GroupNotAllowedException.class);
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void rejectsUnsupportedTypeBeforeStoringAnything() {
        long before = repository.count();
        MockMultipartFile image = new MockMultipartFile("file", "photo.txt", "text/plain", PNG_SIGNATURE);

        assertThatThrownBy(() -> ingest(image, Set.of(), ALICE)).isInstanceOf(UnsupportedDocumentException.class);
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void rejectsCallerWithoutSubject() {
        Entitlements noSubject = new Entitlements(null, null, Set.of("all-staff"), Set.of());

        assertThatThrownBy(() -> ingest(textFile("x.txt", "text"), Set.of(), noSubject))
                .isInstanceOf(AccessDeniedException.class);
    }

    private DocumentEntity ingest(MockMultipartFile file, Set<String> groups, Entitlements caller) {
        DocumentEntity document = ingestionService.ingest(file, groups, caller);
        createdDocuments.add(document.getId());
        return document;
    }

}
