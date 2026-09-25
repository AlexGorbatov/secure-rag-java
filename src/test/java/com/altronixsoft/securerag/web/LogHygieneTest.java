package com.altronixsoft.securerag.web;

import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.altronixsoft.securerag.StubChatModel;
import com.altronixsoft.securerag.TestcontainersConfiguration;
import com.altronixsoft.securerag.repository.DocumentRepository;
import com.altronixsoft.securerag.service.IngestionTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A full upload → search → chat round trip, and a failing chat, leave no document text, question,
 * prompt or answer in the application log. Ids and counts may be logged; content may not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneTest {

    private static final String DOCUMENT_TEXT = "Confidential merger codename Bluefin closes in March.";
    private static final String QUESTION = "When does the Bluefin merger close?";
    private static final String MODEL_ANSWER = "The Bluefin merger closes in March [1].";
    private static final String PROVIDER_ERROR = "rate limit for org-Bluefin-secret";

    @Autowired
    MockMvc mvc;

    @Autowired
    StubChatModel chatModel;

    @Autowired
    DocumentRepository repository;

    @Autowired
    VectorStore vectorStore;

    @Test
    void uploadSearchAndChatLogNoContent(CapturedOutput output) throws Exception {
        chatModel.reset();
        chatModel.replyWith(MODEL_ANSWER);
        UUID document = upload();
        try {
            mvc.perform(post("/api/v1/search").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"" + QUESTION + "\"}").with(alice())).andExpect(status().isOk());
            mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"" + QUESTION + "\"}").with(alice())).andExpect(status().isOk());

            chatModel.failWith(new IllegalStateException(PROVIDER_ERROR));
            mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"" + QUESTION + "\"}").with(alice())).andExpect(status().isBadGateway());

            assertThat(output.getAll())
                    .contains(document.toString())
                    .doesNotContain(DOCUMENT_TEXT)
                    .doesNotContain(QUESTION)
                    .doesNotContain(MODEL_ANSWER)
                    .doesNotContain("Bluefin merger");
        } finally {
            chatModel.reset();
            IngestionTestSupport.remove(repository, vectorStore, List.of(document));
        }
    }

    private UUID upload() throws Exception {
        String body = mvc.perform(multipart("/api/v1/documents").file(textFile("merger.txt", DOCUMENT_TEXT)).with(alice()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token.subject("sub-alice").claim("groups", List.of("all-staff", "hr")));
    }

}
