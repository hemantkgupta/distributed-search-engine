package com.hkg.dse.fst;

import com.hkg.dse.common.Term;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TermDictionaryTest {

    @Test
    void putReturnsMonotonicOrdinals() {
        TermDictionary dict = new TermDictionary();
        int a = dict.put(Term.of("alpha"));
        int b = dict.put(Term.of("beta"));
        assertThat(a).isEqualTo(0);
        assertThat(b).isEqualTo(1);
    }

    @Test
    void repeatedPutReturnsExistingOrdinal() {
        TermDictionary dict = new TermDictionary();
        int first = dict.put(Term.of("hello"));
        int second = dict.put(Term.of("hello"));
        assertThat(first).isEqualTo(second);
        assertThat(dict.size()).isEqualTo(1);
    }

    @Test
    void missingTermReturnsEmptyOptional() {
        TermDictionary dict = new TermDictionary();
        dict.put(Term.of("present"));
        assertThat(dict.get(Term.of("missing"))).isEmpty();
    }

    @Test
    void termsWithPrefixFindsMatches() {
        TermDictionary dict = new TermDictionary();
        dict.put(Term.of("cisco"));
        dict.put(Term.of("circuit"));
        dict.put(Term.of("cisco-router"));
        dict.put(Term.of("router"));

        List<Term> found = new ArrayList<>();
        dict.termsWithPrefix("cisco").forEach(found::add);
        assertThat(found).extracting(Term::text).containsExactlyInAnyOrder("cisco", "cisco-router");
    }

    @Test
    void aclTermsCoexistWithContentTerms() {
        TermDictionary dict = new TermDictionary();
        dict.put(Term.of("cisco"));
        dict.put(Term.aclRead("user_42"));
        dict.put(Term.aclRead("group_admins"));
        assertThat(dict.size()).isEqualTo(3);
        assertThat(dict.contains(Term.aclRead("user_42"))).isTrue();
    }
}
