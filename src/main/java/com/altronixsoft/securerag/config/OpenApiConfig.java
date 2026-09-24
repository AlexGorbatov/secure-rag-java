package com.altronixsoft.securerag.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    static final String LOGIN_SCHEME = "login";

    /**
     * One way to authorize in Swagger UI: the authorization code flow with PKCE against the identity
     * provider. Only this flow is declared, so Swagger UI shows a single login option.
     */
    @Bean
    OpenAPI secureRagOpenApi(
            @Value("${securerag.openapi.authorization-url}") String authorizationUrl,
            @Value("${securerag.openapi.token-url}") String tokenUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("secure-rag-java API")
                        .version("v1")
                        .description("Question answering over documents with need-to-know access control. "
                                + "Every endpoint answers only from documents the caller is entitled to read."))
                .components(new Components()
                        .addSecuritySchemes(LOGIN_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Log in with your identity provider account")
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authorizationUrl)
                                                .tokenUrl(tokenUrl)
                                                .scopes(new Scopes())))))
                .addSecurityItem(new SecurityRequirement().addList(LOGIN_SCHEME));
    }

}
