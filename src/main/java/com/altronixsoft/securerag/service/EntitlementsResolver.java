package com.altronixsoft.securerag.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.altronixsoft.securerag.config.ClaimsProperties;
import com.altronixsoft.securerag.model.Entitlements;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps a verified JWT to {@link Entitlements}. Missing or malformed claims yield empty sets — never
 * a default group — so a token the mapping does not understand grants nothing.
 */
@Component
public class EntitlementsResolver {

    private final ClaimsProperties claims;

    public EntitlementsResolver(ClaimsProperties claims) {
        this.claims = claims;
    }

    public Entitlements resolve(Jwt jwt) {
        return new Entitlements(
                jwt.getSubject(),
                claimAsString(jwt, claims.username()),
                claimAsStrings(jwt, claims.groups()),
                claimAsStrings(jwt, claims.roles()));
    }

    private static String claimAsString(Jwt jwt, String path) {
        return lookup(jwt, path) instanceof String value ? value : null;
    }

    private static Set<String> claimAsStrings(Jwt jwt, String path) {
        if (!(lookup(jwt, path) instanceof Collection<?> values)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof String string && !string.isBlank()) {
                result.add(string);
            }
        }
        return result;
    }

    private static Object lookup(Jwt jwt, String path) {
        Object current = jwt.getClaims();
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

}
