package com.altronixsoft.securerag.service;

import com.altronixsoft.securerag.model.Entitlements;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.Optional;

/**
 * The only place that turns a caller's entitlements into a vector-store filter:
 * {@code owner == subject OR allowed_groups contains any of the caller's groups}.
 *
 * <p>The filter runs inside the pgvector query, so chunks the caller may not read are never loaded.
 * It is built with {@link FilterExpressionBuilder} from token-derived values only, never concatenated
 * from strings and never taken from request input. A chunk without {@code owner} and
 * {@code allowed_groups} metadata matches neither branch, so it is invisible to everyone.
 */
public final class AccessFilter {

    private AccessFilter() {
    }

    /**
     * Empty when the caller has no subject: such a caller may see nothing, so there is nothing to
     * search.
     */
    public static Optional<Filter.Expression> visibleTo(Entitlements caller) {
        if (caller.subject() == null) {
            return Optional.empty();
        }
        FilterExpressionBuilder filter = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op ownChunks = filter.eq(ChunkMetadata.OWNER, caller.subject());
        if (caller.groups().isEmpty()) {
            return Optional.of(ownChunks.build());
        }
        FilterExpressionBuilder.Op sharedChunks = filter.in(ChunkMetadata.ALLOWED_GROUPS, caller.groups().toArray());
        return Optional.of(filter.or(ownChunks, sharedChunks).build());
    }

}
