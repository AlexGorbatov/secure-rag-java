package com.altronixsoft.securerag.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class OpenApiDocsTest {

    @Autowired
    MockMvc mvc;

    @Test
    void apiDocumentIsPublicAndDescribesEndpointsAndSecurity() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("secure-rag-java API"))
                .andExpect(jsonPath("$.paths['/api/v1/me'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.login.flows.authorizationCode.authorizationUrl")
                        .value("http://localhost:8180/realms/securerag/protocol/openid-connect/auth"))
                .andExpect(jsonPath("$.components.securitySchemes.login.flows.authorizationCode.tokenUrl")
                        .value("http://localhost:8180/realms/securerag/protocol/openid-connect/token"));
    }

    @Test
    void offersOnlyTheAuthorizationCodeLogin() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.length()").value(1))
                .andExpect(jsonPath("$.components.securitySchemes.login.flows.length()").value(1))
                .andExpect(jsonPath("$.security.length()").value(1));
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

}
