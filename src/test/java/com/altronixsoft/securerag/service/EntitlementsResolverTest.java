package com.altronixsoft.securerag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.altronixsoft.securerag.config.ClaimsProperties;
import com.altronixsoft.securerag.model.Entitlements;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class EntitlementsResolverTest {

    private static final ClaimsProperties KEYCLOAK = new ClaimsProperties("preferred_username", "groups", "realm_access.roles");
    private static final ClaimsProperties ENTRA_ID = new ClaimsProperties("preferred_username", "groups", "roles");

    @Test
    void readsKeycloakShapedToken() {
        Jwt jwt = token(claims -> claims
                .claim("preferred_username", "alice")
                .claim("groups", List.of("all-staff", "hr"))
                .claim("realm_access", Map.of("roles", List.of("user"))));

        Entitlements entitlements = new EntitlementsResolver(KEYCLOAK).resolve(jwt);

        assertThat(entitlements.subject()).isEqualTo("sub-alice");
        assertThat(entitlements.username()).isEqualTo("alice");
        assertThat(entitlements.groups()).containsExactlyInAnyOrder("all-staff", "hr");
        assertThat(entitlements.roles()).containsExactly("user");
    }

    @Test
    void readsTopLevelRolesClaimWhenConfiguredForEntraId() {
        Jwt jwt = token(claims -> claims.claim("roles", List.of("user")));

        assertThat(new EntitlementsResolver(ENTRA_ID).resolve(jwt).roles()).containsExactly("user");
    }

    @Test
    void grantsNothingWhenGroupAndRoleClaimsAreMissing() {
        Entitlements entitlements = new EntitlementsResolver(KEYCLOAK).resolve(token(claims -> {}));

        assertThat(entitlements.groups()).isEmpty();
        assertThat(entitlements.roles()).isEmpty();
    }

    @Test
    void grantsNothingWhenClaimsHaveUnexpectedShape() {
        Jwt jwt = token(claims -> claims
                .claim("groups", "hr")
                .claim("realm_access", List.of("user")));

        Entitlements entitlements = new EntitlementsResolver(KEYCLOAK).resolve(jwt);

        assertThat(entitlements.groups()).isEmpty();
        assertThat(entitlements.roles()).isEmpty();
    }

    @Test
    void ignoresBlankAndNonStringGroupEntries() {
        Jwt jwt = token(claims -> claims.claim("groups", List.of("hr", "", " ", 42)));

        assertThat(new EntitlementsResolver(KEYCLOAK).resolve(jwt).groups()).containsExactly("hr");
    }

    private static Jwt token(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("sub-alice")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T00:15:00Z"));
        claims.accept(builder);
        return builder.build();
    }

}
