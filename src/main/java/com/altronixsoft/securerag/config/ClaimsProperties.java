package com.altronixsoft.securerag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the identity provider puts each piece of information in the token. Nested claims use a
 * dotted path. Defaults match Keycloak; for Entra ID set {@code roles=roles}.
 */
@ConfigurationProperties("securerag.security.claims")
public record ClaimsProperties(
        @DefaultValue("preferred_username") String username,
        @DefaultValue("groups") String groups,
        @DefaultValue("realm_access.roles") String roles) {
}
