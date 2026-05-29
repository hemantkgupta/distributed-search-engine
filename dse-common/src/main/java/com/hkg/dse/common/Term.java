package com.hkg.dse.common;

import java.util.Objects;

/**
 * A term (token) in the inverted index.
 *
 * <p>Terms include both ordinary content tokens ("cisco", "router") and
 * synthetic terms used for ACL pre-filtering ({@code _acl_read:user_42},
 * {@code _acl_read:group_admins}). The inverted-index machinery does not
 * distinguish the two — both are just strings the FST term dictionary
 * maps to posting-list offsets.</p>
 *
 * <p>Co-locating ACL terms in the same inverted index as content terms
 * is what makes the ACL pre-filter (a boolean OR over ACL posting lists)
 * cheap and fast — see {@code tradeoffs/pre-filter-vs-post-filter-acl}.</p>
 */
public record Term(String text) implements Comparable<Term> {

    public Term {
        Objects.requireNonNull(text, "term text must not be null");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("term text must not be empty");
        }
    }

    /** Prefix used for the access-control-list read tokens. */
    public static final String ACL_READ_PREFIX = "_acl_read:";

    public static Term aclRead(String principalToken) {
        return new Term(ACL_READ_PREFIX + principalToken);
    }

    public boolean isAclToken() {
        return text.startsWith(ACL_READ_PREFIX);
    }

    public static Term of(String text) {
        return new Term(text);
    }

    @Override
    public int compareTo(Term other) {
        return this.text.compareTo(other.text);
    }
}
