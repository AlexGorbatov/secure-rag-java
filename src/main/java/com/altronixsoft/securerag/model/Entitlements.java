package com.altronixsoft.securerag.model;

import java.util.Set;

/**
 * What the caller may see, derived only from a verified JWT. This is the single input to every
 * access decision; nothing from the request path, query, headers or body ever widens it.
 *
 * @param subject  the token's {@code sub}; owner identity for documents
 * @param username human-readable name, for display only — never use it for access decisions
 * @param groups   group memberships; documents can be shared with a group
 * @param roles    realm roles
 */
public record Entitlements(String subject, String username, Set<String> groups, Set<String> roles) {

    public Entitlements {
        groups = Set.copyOf(groups);
        roles = Set.copyOf(roles);
    }

}
