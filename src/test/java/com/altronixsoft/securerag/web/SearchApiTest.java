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

/**
 * Search through the HTTP API with documents uploaded by Alice: one for HR only, one for all staff,
 * both about salaries so Bob's query would match both without the access filter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SearchApiTest {

    private static final String SEARCH = "/api/v1/search";
    private static final String HR_TITLE = "hr-salary-bands.txt";
    private static final String SHARED_TITLE = "salary-payment-dates.txt";

    @Autowired
    MockMvc mvc;

    @Autowired
    DocumentRepository repository;

    @Autowired
    VectorStore vectorStore;

    private final List<UUID> createdDocuments = new ArrayList<>();
    private UUID hrDocument;
    private UUID sharedDocument;

    @BeforeEach
    void aliceUploadsDocuments() throws Exception {
        hrDocument = upload(HR_TITLE, "Salary bands for 2026: engineers earn between two agreed limits.", "hr");
        sharedDocument = upload(SHARED_TITLE, "Salaries are paid on the last working day of each month.", "all-staff");
    }

    @AfterEach
    void removeCreatedDocuments() {
        IngestionTestSupport.remove(repository, vectorStore, createdDocuments);
    }

    @Test
    void searchWithoutTokenIs401() throws Exception {
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON).content(query("salary")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliceFindsBothHerDocuments() throws Exception {
        String body = search("salary", alice());

        assertThat(documentIds(body)).contains(hrDocument.toString(), sharedDocument.toString());
    }

    @Test
    void bobFindsOnlyTheSharedDocumentAndNothingOfTheHrOne() throws Exception {
        String body = search("salary", bob());

        assertThat(documentIds(body)).contains(sharedDocument.toString()).doesNotContain(hrDocument.toString());
        assertThat(body).doesNotContain(HR_TITLE).doesNotContain("Salary bands");
    }

    @Test
    void hitCarriesTitleSnippetAndScore() throws Exception {
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON).content(query("when are salaries paid?")).with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(sharedDocument.toString()))
                .andExpect(jsonPath("$[0].title").value(SHARED_TITLE))
                .andExpect(jsonPath("$[0].snippet").value("Salaries are paid on the last working day of each month."))
                .andExpect(jsonPath("$[0].score").isNumber());
    }

    @Test
    void blankQueryIs400() throws Exception {
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON).content(query(" ")).with(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("query"));
    }

    @Test
    void topKAboveLimitIs400() throws Exception {
        mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"salary\",\"topK\":500}").with(alice()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("topK"));
    }

    @Test
    void groupsInRequestBodyAreIgnored() throws Exception {
        String body = mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"salary\",\"groups\":[\"hr\"],\"owner\":\"sub-alice\"}").with(bob()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(documentIds(body)).doesNotContain(hrDocument.toString());
    }

    private String search(String text, JwtRequestPostProcessor caller) throws Exception {
        return mvc.perform(post(SEARCH).contentType(MediaType.APPLICATION_JSON).content(query(text)).with(caller))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID upload(String title, String text, String group) throws Exception {
        String body = mvc.perform(multipart("/api/v1/documents").file(textFile(title, text)).param("groups", group).with(alice()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        createdDocuments.add(id);
        return id;
    }

    private static String query(String text) {
        return "{\"query\":\"" + text + "\"}";
    }

    private static List<String> documentIds(String body) {
        return JsonPath.read(body, "$[*].documentId");
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token.subject("sub-alice").claim("groups", List.of("all-staff", "hr")));
    }

    private static JwtRequestPostProcessor bob() {
        return jwt().jwt(token -> token.subject("sub-bob").claim("groups", List.of("all-staff", "engineering")));
    }

}
