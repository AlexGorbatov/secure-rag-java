package com.altronixsoft.securerag.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static final String OIDC_SCHEME = "oidc";
    static final String BEARER_SCHEME = "bearer";

    /**
     * Two interchangeable ways to authorize in Swagger UI: log in through the identity provider
     * (OpenID Connect discovery works for Keycloak and Entra ID alike), or paste an access token.
     */
    @Bean
    OpenAPI secureRagOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return new OpenAPI()
                .info(new Info()
                        .title("secure-rag-java API")
                        .version("v1")
                        .description("Question answering over documents with need-to-know access control. "
                                + "Every endpoint answers only from documents the caller is entitled to read."))
                .components(new Components()
                        .addSecuritySchemes(OIDC_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OPENIDCONNECT)
                                .openIdConnectUrl(issuerUri + "/.well-known/openid-configuration"))
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(OIDC_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

}
