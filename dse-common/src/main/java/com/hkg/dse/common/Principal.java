package com.hkg.dse.common;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An authenticated principal — the result of expanding a session token
 * into the set of access tokens the shard's inverted index should
 * intersect against.
 *
 * <p>Principal expansion typically happens at the edge or broker, by
 * looking up the user's identity → groups → roles. The expanded set is
 * the union of all access tokens the user holds. The shard uses this
 * set to build a Roaring ACL bitmap that pre-filters both the lexical
 * loop and the HNSW vector traversal.</p>
 *
 * <p>The user_id token is always included implicitly, so a user holding
 * no groups still has at least one access token.</p>
 */
public final class Principal {

    private final String userId;
    private final String tenantId;
    private final Set<String> aclTokens;

    public Principal(String userId, String tenantId, Set<String> aclTokens) {
        this.userId = Objects.requireNonNull(userId);
        this.tenantId = Objects.requireNonNull(tenantId);
        Set<String> defensive = new LinkedHashSet<>();
        defensive.add("user_" + userId);
        defensive.addAll(aclTokens);
        this.aclTokens = Set.copyOf(defensive);
    }

    public String userId() {
        return userId;
    }

    public String tenantId() {
        return tenantId;
    }

    public Set<String> aclTokens() {
        return aclTokens;
    }
}
