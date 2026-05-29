package com.hkg.dse.common;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalTest {

    @Test
    void userTokenAlwaysIncluded() {
        Principal p = new Principal("42", "acme", Set.of("group_engineering"));
        assertThat(p.aclTokens()).contains("user_42", "group_engineering");
    }

    @Test
    void aclTokensAreImmutable() {
        Set<String> input = new java.util.HashSet<>(Set.of("group_a"));
        Principal p = new Principal("1", "t", input);
        input.add("group_b");  // should not affect p
        assertThat(p.aclTokens()).doesNotContain("group_b");
    }
}
