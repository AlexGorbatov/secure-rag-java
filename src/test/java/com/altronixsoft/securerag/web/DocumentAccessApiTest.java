package com.altronixsoft.securerag.web;

import static com.altronixsoft.securerag.service.IngestionTestSupport.chunksOf;
import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who can list, read and delete which document, through the real HTTP API. Alice uploads one HR-only
 * document and one shared with all staff; Bob is in all-staff but not in hr.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentAccessApiTest {

    private static final String DOCUMENTS = "/api/v1/documents";
    private static final String HR_TITLE = "hr-salary-bands.txt";
    private static final String SHARED_TITLE = "office-hours.txt";

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
        hrDocument = upload(HR_TITLE, "Salary bands for 2026: engineers earn between two limits.", "hr");
        sharedDocument = upload(SHARED_TITLE, "The office is open from nine to six on weekdays.", "all-staff");
    }

    @AfterEach
    void removeCreatedDocuments() {
        IngestionTestSupport.remove(repository, vectorStore, createdDocuments);
    }

    @Test
    void listWithoutTokenIs401() throws Exception {
        mvc.perform(get(DOCUMENTS)).andExpect(status().isUnauthorized());
    }

    @Test
    void aliceListsHerPrivateAndSharedDocuments() throws Exception {
        String body = mvc.perform(get(DOCUMENTS).with(alice()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(body)).contains(hrDocument.toString(), sharedDocument.toString());
    }

    @Test
    void bobListsOnlyWhatIsSharedWithHisGroups() throws Exception {
        String body = mvc.perform(get(DOCUMENTS).with(bob()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(body)).contains(sharedDocument.toString()).doesNotContain(hrDocument.toString());
        assertThat(body).doesNotContain(HR_TITLE);
    }

    @Test
    void bobGettingAlicesHrDocumentIs404AndRevealsNothing() throws Exception {
        String body = mvc.perform(get(DOCUMENTS + "/" + hrDocument).with(bob()))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(HR_TITLE).doesNotContain("Salary");
    }

    @Test
    void bobGetsDocumentSharedWithAllStaff() throws Exception {
        mvc.perform(get(DOCUMENTS + "/" + sharedDocument).with(bob()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(SHARED_TITLE));
    }

    @Test
    void unknownDocumentIs404LikeAForeignOne() throws Exception {
        mvc.perform(get(DOCUMENTS + "/" + UUID.randomUUID()).with(alice()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found"));
    }

    @Test
    void malformedIdIs400() throws Exception {
        mvc.perform(get(DOCUMENTS + "/not-a-uuid").with(alice()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenWithoutSubjectSeesNothing() throws Exception {
        JwtRequestPostProcessor noSubject = jwt().jwt(token -> token
                .claims(claims -> claims.remove("sub"))
                .claim("groups", List.of("all-staff", "hr")));

        mvc.perform(get(DOCUMENTS).with(noSubject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(DOCUMENTS + "/" + sharedDocument).with(noSubject))
                .andExpect(status().isNotFound());
    }

    @Test
    void bobCannotDeleteAlicesDocumentEvenWhenItIsSharedWithHim() throws Exception {
        mvc.perform(delete(DOCUMENTS + "/" + sharedDocument).with(bob()))
                .andExpect(status().isNotFound());

        assertThat(repository.findById(sharedDocument)).isPresent();
        assertThat(chunksOf(vectorStore, sharedDocument)).isNotEmpty();
    }

    @Test
    void ownerDeleteRemovesDocumentAndAllItsChunks() throws Exception {
        mvc.perform(delete(DOCUMENTS + "/" + hrDocument).with(alice()))
                .andExpect(status().isNoContent());

        mvc.perform(get(DOCUMENTS + "/" + hrDocument).with(alice()))
                .andExpect(status().isNotFound());
        assertThat(repository.findById(hrDocument)).isEmpty();
        assertThat(chunksOf(vectorStore, hrDocument)).isEmpty();
    }

    private UUID upload(String title, String text, String group) throws Exception {
        String body = mvc.perform(multipart(DOCUMENTS).file(textFile(title, text)).param("groups", group).with(alice()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        createdDocuments.add(id);
        return id;
    }

    private static List<String> ids(String listBody) {
        return JsonPath.read(listBody, "$[*].id");
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token
                .subject("sub-alice")
                .claim("preferred_username", "alice")
                .claim("groups", List.of("all-staff", "hr")));
    }

    private static JwtRequestPostProcessor bob() {
        return jwt().jwt(token -> token
                .subject("sub-bob")
                .claim("preferred_username", "bob")
                .claim("groups", List.of("all-staff", "engineering")));
    }

}
