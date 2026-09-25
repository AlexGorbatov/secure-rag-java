package com.altronixsoft.securerag.web;

import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.altronixsoft.securerag.StubChatModel;
import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.IngestionTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ChatApiTest {

    private static final String CHAT = "/api/v1/chat";
    private static final String HR_TITLE = "hr-salary-bands.txt";
    private static final String HR_TEXT = "Salary bands for 2026: engineers earn between two agreed limits.";

    @Autowired
    MockMvc mvc;

    @Autowired
    DocumentRepository repository;

    @Autowired
    VectorStore vectorStore;

    @Autowired
    StubChatModel chatModel;

    private final List<UUID> createdDocuments = new ArrayList<>();
    private UUID hrDocument;

    @BeforeEach
    void aliceUploadsHrDocument() throws Exception {
        chatModel.reset();
        String body = mvc.perform(multipart("/api/v1/documents").file(textFile(HR_TITLE, HR_TEXT)).param("groups", "hr").with(alice()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        hrDocument = UUID.fromString(JsonPath.read(body, "$.id"));
        createdDocuments.add(hrDocument);
    }

    @AfterEach
    void removeCreatedDocuments() {
        IngestionTestSupport.remove(repository, vectorStore, createdDocuments);
    }

    @Test
    void chatWithoutTokenIs401() throws Exception {
        mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(question("salary?")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankQuestionIs400() throws Exception {
        mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(question(" ")).with(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("question"));
    }

    @Test
    void aliceGetsAnswerWithCitationOfHerDocument() throws Exception {
        chatModel.replyWith("Engineers earn within the 2026 bands [1].");

        mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(question("What do engineers earn?")).with(alice()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Engineers earn within the 2026 bands [1]."))
                .andExpect(jsonPath("$.citations[0].documentId").value(hrDocument.toString()))
                .andExpect(jsonPath("$.citations[0].title").value(HR_TITLE));
    }

    @Test
    void bobGetsNoAnswerFromAlicesHrDocument() throws Exception {
        String body = mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(question("What do engineers earn?")).with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citations").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HR_TITLE).doesNotContain(hrDocument.toString());
        assertThat(chatModel.allPromptText()).doesNotContain(HR_TEXT);
    }

    @Test
    void modelFailureIs502WithoutProviderDetails() throws Exception {
        chatModel.failWith(new IllegalStateException("OpenAI 429: rate limit for org-secret-123"));

        String body = mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content(question("What do engineers earn?")).with(alice()))
                .andExpect(status().isBadGateway())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("org-secret-123").doesNotContain("OpenAI");
    }

    private static String question(String text) {
        return "{\"question\":\"" + text + "\"}";
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token.subject("sub-alice").claim("groups", List.of("all-staff", "hr")));
    }

    private static JwtRequestPostProcessor bob() {
        return jwt().jwt(token -> token.subject("sub-bob").claim("groups", List.of("all-staff", "engineering")));
    }

}
