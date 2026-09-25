package com.altronixsoft.securerag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.altronixsoft.securerag.StubChatModel;
import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.model.Entitlements;
import com.altronixsoft.securerag.service.exception.AnswerGenerationFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * What reaches the model, and what comes back as citations. The stub model records every prompt, so
 * "Bob's prompt never contains Alice's text" is asserted on what was actually sent.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ChatServiceTest {

    private static final String ALICE_HR_TEXT = "Salary bands for 2026: engineers earn between two agreed limits.";
    private static final String BOB_TEXT = "Salary review process for the engineering team happens every spring.";
    private static final String QUESTION = "What do engineers earn?";

    private static final Entitlements ALICE = new Entitlements("sub-alice", "alice", Set.of("all-staff", "hr"), Set.of());
    private static final Entitlements BOB = new Entitlements("sub-bob", "bob", Set.of("all-staff", "engineering"), Set.of());

    @Autowired
    ChatService chatService;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    StubChatModel chatModel;

    private final List<String> addedChunks = new ArrayList<>();

    @BeforeEach
    void resetModel() {
        chatModel.reset();
    }

    @AfterEach
    void removeChunks() {
        if (!addedChunks.isEmpty()) {
            vectorStore.delete(addedChunks);
        }
    }

    @Test
    void bobsPromptNeverContainsAlicesHrText() {
        addChunk(UUID.randomUUID(), "hr-salaries.txt", ALICE_HR_TEXT, "sub-alice", List.of("hr"));
        addChunk(UUID.randomUUID(), "review.txt", BOB_TEXT, "sub-bob", List.of("engineering"));

        chatService.answer(QUESTION, BOB);

        assertThat(chatModel.prompts()).hasSize(1);
        assertThat(chatModel.allPromptText()).contains(BOB_TEXT).doesNotContain(ALICE_HR_TEXT).doesNotContain("hr-salaries.txt");
    }

    @Test
    void answerIsTheModelsTextAndCitationsAreTheRetrievedDocuments() {
        UUID document = UUID.randomUUID();
        addChunk(document, "hr-salaries.txt", ALICE_HR_TEXT, "sub-alice", List.of("hr"));
        chatModel.replyWith("Engineers earn within the bands [1]. See also [7] secret-plan.pdf.");

        ChatAnswer answer = chatService.answer(QUESTION, ALICE);

        assertThat(answer.answer()).isEqualTo("Engineers earn within the bands [1]. See also [7] secret-plan.pdf.");
        assertThat(answer.citations()).containsExactly(new ChatAnswer.Citation(document, "hr-salaries.txt"));
    }

    @Test
    void documentWithSeveralRetrievedChunksIsCitedOnce() {
        UUID document = UUID.randomUUID();
        addChunk(document, "hr-salaries.txt", ALICE_HR_TEXT, "sub-alice", List.of());
        addChunk(document, "hr-salaries.txt", "Engineer salaries are reviewed against the bands yearly.", "sub-alice", List.of());

        assertThat(chatService.answer(QUESTION, ALICE).citations())
                .containsExactly(new ChatAnswer.Citation(document, "hr-salaries.txt"));
    }

    @Test
    void withoutPermittedContextTheModelIsNotCalled() {
        addChunk(UUID.randomUUID(), "hr-salaries.txt", ALICE_HR_TEXT, "sub-alice", List.of("hr"));

        ChatAnswer answer = chatService.answer(QUESTION, BOB);

        assertThat(answer.answer()).isEqualTo(ChatService.NO_CONTEXT_ANSWER);
        assertThat(answer.citations()).isEmpty();
        assertThat(chatModel.prompts()).isEmpty();
    }

    @Test
    void callerWithoutSubjectGetsNoAnswerFromSharedDocuments() {
        Entitlements noSubject = new Entitlements(null, null, Set.of("all-staff"), Set.of());
        addChunk(UUID.randomUUID(), "shared.txt", ALICE_HR_TEXT, "sub-alice", List.of("all-staff"));

        assertThat(chatService.answer(QUESTION, noSubject).answer()).isEqualTo(ChatService.NO_CONTEXT_ANSWER);
        assertThat(chatModel.prompts()).isEmpty();
    }

    @Test
    void modelFailureBecomesAnswerGenerationFailed() {
        addChunk(UUID.randomUUID(), "hr-salaries.txt", ALICE_HR_TEXT, "sub-alice", List.of());
        chatModel.failWith(new IllegalStateException("provider timeout"));

        assertThatThrownBy(() -> chatService.answer(QUESTION, ALICE))
                .isInstanceOf(AnswerGenerationFailedException.class)
                .hasRootCauseMessage("provider timeout");
    }

    @Test
    void blankModelReplyIsAFailureNotAnEmptyAnswer() {
        addChunk(UUID.randomUUID(), "hr-salaries.txt", ALICE_HR_TEXT, "sub-alice", List.of());
        chatModel.replyWith(" ");

        assertThatThrownBy(() -> chatService.answer(QUESTION, ALICE))
                .isInstanceOf(AnswerGenerationFailedException.class);
    }

    private void addChunk(UUID documentId, String title, String text, String owner, List<String> groups) {
        Document chunk = Document.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .metadata(Map.of(
                        ChunkMetadata.DOCUMENT_ID, documentId.toString(),
                        ChunkMetadata.TITLE, title,
                        ChunkMetadata.OWNER, owner,
                        ChunkMetadata.ALLOWED_GROUPS, groups))
                .build();
        vectorStore.add(List.of(chunk));
        addedChunks.add(chunk.getId());
    }

}
