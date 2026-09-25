package com.altronixsoft.securerag.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The static frontend bundle must be reachable before login; nothing under /api/** may loosen.
 * No frontend is built in the test classpath, so a permitted path resolves to 404 (no file), not
 * 401 (blocked by the security filter) — that distinction is exactly what this test checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StaticAssetsSecurityTest {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/favicon.ico", "/_next/static/chunk.js"})
    void staticAssetPathsAreReachableWithoutAToken(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void apiStillRequiresAToken() throws Exception {
        mvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized());
    }

}
