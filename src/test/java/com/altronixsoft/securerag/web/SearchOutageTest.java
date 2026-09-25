package com.altronixsoft.securerag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * When the embedding provider or vector store is down, search and chat answer 503 with a neutral
 * message instead of an internal error that might carry provider details.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SearchOutageTest {

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    VectorStore vectorStore;

    @BeforeEach
    void embeddingProviderIsDown() {
        doThrow(new IllegalStateException("Azure OpenAI 401 for key sk-leaked-123"))
                .when(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @ParameterizedTest
    @CsvSource({"/api/v1/search, query", "/api/v1/chat, question"})
    void outageIs503WithoutProviderDetails(String path, String field) throws Exception {
        String body = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"" + field + "\":\"salary\"}")
                        .with(jwt().jwt(token -> token.subject("sub-alice").claim("groups", List.of("hr")))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Search is temporarily unavailable. Please try again later."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("sk-leaked-123").doesNotContain("Azure");
    }

}
