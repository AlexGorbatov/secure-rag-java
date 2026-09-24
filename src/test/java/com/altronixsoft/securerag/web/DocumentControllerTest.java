package com.altronixsoft.securerag.web;

import static com.altronixsoft.securerag.service.IngestionTestSupport.textFile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DocumentControllerTest {

    private static final String DOCUMENTS = "/api/v1/documents";

    @Autowired
    MockMvc mvc;

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
    void uploadWithoutTokenIs401() throws Exception {
        mvc.perform(multipart(DOCUMENTS).file(policy()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadCreatesReadyDocumentSharedWithCallersGroup() throws Exception {
        MockHttpServletResponse response = mvc.perform(multipart(DOCUMENTS).file(policy()).param("groups", "all-staff").with(alice()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.title").value("policy.txt"))
                .andExpect(jsonPath("$.allowedGroups[0]").value("all-staff"))
                .andExpect(jsonPath("$.chunkCount").value(greaterThan(0)))
                .andReturn().getResponse();

        UUID id = remember(response.getContentAsString());
        assertThat(response.getHeader("Location")).endsWith(DOCUMENTS + "/" + id);
        assertThat(repository.findById(id)).isPresent();
    }

    @Test
    void ownerComesFromTokenNotFromRequest() throws Exception {
        String body = mvc.perform(multipart(DOCUMENTS).file(policy()).param("owner", "sub-bob").with(alice()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(repository.findById(remember(body))).get()
                .satisfies(document -> assertThat(document.getOwnerSub()).isEqualTo("sub-alice"));
    }

    @Test
    void sharingWithForeignGroupIs403() throws Exception {
        mvc.perform(multipart(DOCUMENTS).file(policy()).param("groups", "engineering").with(alice()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("A document can only be shared with groups you belong to"));
    }

    @Test
    void unsupportedFileIs415() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "photo.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0});

        mvc.perform(multipart(DOCUMENTS).file(image).with(alice()))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static MockMultipartFile policy() {
        return textFile("policy.txt", "Vacation policy: employees get 25 days of paid leave per year.");
    }

    private static JwtRequestPostProcessor alice() {
        return jwt().jwt(token -> token
                .subject("sub-alice")
                .claim("preferred_username", "alice")
                .claim("groups", List.of("all-staff", "hr")));
    }

    private UUID remember(String responseBody) {
        UUID id = UUID.fromString(JsonPath.read(responseBody, "$.id"));
        createdDocuments.add(id);
        return id;
    }

}
