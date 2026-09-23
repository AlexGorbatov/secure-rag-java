package com.altronixsoft.securerag.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CurrentUserApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEntitlementsFromToken() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
                        .subject("sub-bob")
                        .claim("preferred_username", "bob")
                        .claim("groups", List.of("all-staff", "engineering"))
                        .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("sub-bob"))
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.groups.length()").value(2))
                .andExpect(jsonPath("$.groups[?(@ == 'engineering')]").exists())
                .andExpect(jsonPath("$.roles[0]").value("user"));
    }

    @Test
    void tokenWithoutGroupClaimGrantsNoGroups() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject("sub-carol"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void publicGetRoutesDoNotOpenOtherMethods() throws Exception {
        mvc.perform(post("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

}
