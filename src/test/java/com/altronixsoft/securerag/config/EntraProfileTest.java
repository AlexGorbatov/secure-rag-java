package com.altronixsoft.securerag.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.altronixsoft.securerag.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The entra profile switches the identity provider by configuration only: issuer, audience, the claim
 * holding roles and the Swagger UI login endpoints. No token is validated here (the JWT decoder
 * contacts the issuer lazily), so the test runs without Entra ID.
 */
@SpringBootTest(properties = {
        "ENTRA_TENANT_ID=11111111-2222-3333-4444-555555555555",
        "ENTRA_API_AUDIENCE=api://secure-rag-java",
        "ENTRA_SWAGGER_CLIENT_ID=swagger-client"})
@ActiveProfiles({"test", "entra"})
@Import(TestcontainersConfiguration.class)
class EntraProfileTest {

    private static final String TENANT = "https://login.microsoftonline.com/11111111-2222-3333-4444-555555555555";

    @Autowired
    Environment environment;

    @Autowired
    ClaimsProperties claims;

    @Test
    void tokensAreExpectedFromEntraIdForThisApi() {
        assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .isEqualTo(TENANT + "/v2.0");
        assertThat(environment.getProperty("spring.security.oauth2.resourceserver.jwt.audiences"))
                .isEqualTo("api://secure-rag-java");
    }

    @Test
    void rolesAreReadFromTheTopLevelRolesClaim() {
        assertThat(claims.roles()).isEqualTo("roles");
        assertThat(claims.groups()).isEqualTo("groups");
    }

    @Test
    void swaggerUiLogsInAgainstEntraId() {
        assertThat(environment.getProperty("securerag.openapi.authorization-url"))
                .isEqualTo(TENANT + "/oauth2/v2.0/authorize");
        assertThat(environment.getProperty("securerag.openapi.token-url"))
                .isEqualTo(TENANT + "/oauth2/v2.0/token");
        assertThat(environment.getProperty("springdoc.swagger-ui.oauth.client-id")).isEqualTo("swagger-client");
    }

}
