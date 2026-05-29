package com.hkg.dse.acl;

import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;
import com.hkg.dse.inverted.InvertedIndex;
import com.hkg.dse.roaring.RoaringBitset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AclBitsetBuilderTest {

    @Test
    void buildsBitsetFromPrincipalTokens() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.of("text"), Term.aclRead("user_42")))
            .addDocument(1, List.of(Term.of("text"), Term.aclRead("group_admins")))
            .addDocument(2, List.of(Term.of("text"), Term.aclRead("group_others")))
            .build();

        AclBitsetBuilder builder = new AclBitsetBuilder(idx);
        Principal principal = new Principal("42", "t", Set.of("group_admins"));
        RoaringBitset acl = builder.buildFor(principal);

        // Should authorise docs 0 (user_42) and 1 (group_admins).
        assertThat(acl.contains(0)).isTrue();
        assertThat(acl.contains(1)).isTrue();
        assertThat(acl.contains(2)).isFalse();
        assertThat(acl.cardinality()).isEqualTo(2);
    }

    @Test
    void missingAclTermsContributeNothing() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.of("text"), Term.aclRead("user_1")))
            .build();
        AclBitsetBuilder builder = new AclBitsetBuilder(idx);
        Principal principal = new Principal("999", "t", Set.of("group_nobody"));
        RoaringBitset acl = builder.buildFor(principal);
        assertThat(acl.isEmpty()).isTrue();
    }

    @Test
    void userTokenAlwaysContributes() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.aclRead("user_42")))
            .addDocument(1, List.of(Term.aclRead("user_99")))
            .build();
        AclBitsetBuilder builder = new AclBitsetBuilder(idx);
        Principal principal = new Principal("42", "t", Set.of());  // no groups
        RoaringBitset acl = builder.buildFor(principal);
        // User_42's own token is added implicitly by Principal — should authorise doc 0
        assertThat(acl.contains(0)).isTrue();
        assertThat(acl.contains(1)).isFalse();
    }
}
