package com.hkg.dse.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TermTest {

    @Test
    void rejectsEmptyText() {
        assertThatThrownBy(() -> Term.of(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aclReadConstructorPrependsPrefix() {
        Term t = Term.aclRead("user_42");
        assertThat(t.text()).isEqualTo("_acl_read:user_42");
        assertThat(t.isAclToken()).isTrue();
    }

    @Test
    void contentTermIsNotAcl() {
        assertThat(Term.of("cisco").isAclToken()).isFalse();
    }

    @Test
    void sortableAlphabetically() {
        Term a = Term.of("apple");
        Term b = Term.of("banana");
        assertThat(a.compareTo(b)).isNegative();
    }
}
