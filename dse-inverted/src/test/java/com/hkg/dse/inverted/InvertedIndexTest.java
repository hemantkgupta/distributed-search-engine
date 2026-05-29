package com.hkg.dse.inverted;

import com.hkg.dse.common.Term;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvertedIndexTest {

    @Test
    void postingsForReturnsEmptyWhenTermAbsent() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.of("hello")))
            .build();
        assertThat(idx.postingsFor(Term.of("missing"))).isEmpty();
    }

    @Test
    void postingsContainExpectedDocs() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.of("cisco"), Term.of("router")))
            .addDocument(1, List.of(Term.of("cisco"), Term.of("switch")))
            .addDocument(2, List.of(Term.of("juniper"), Term.of("router")))
            .build();
        assertThat(idx.postingsFor(Term.of("cisco"))).isPresent();
        assertThat(idx.postingsFor(Term.of("cisco")).get().docFrequency()).isEqualTo(2);
        assertThat(idx.postingsFor(Term.of("router"))).isPresent();
        assertThat(idx.postingsFor(Term.of("router")).get().docFrequency()).isEqualTo(2);
    }

    @Test
    void totalDocsReflectsDocumentCount() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.of("a")))
            .addDocument(1, List.of(Term.of("b")))
            .addDocument(2, List.of(Term.of("c")))
            .build();
        assertThat(idx.totalDocs()).isEqualTo(3);
    }

    @Test
    void avgDocLengthReflectsCorpus() {
        InvertedIndex idx = new InvertedIndex.Builder()
            .addDocument(0, List.of(Term.of("a"), Term.of("b"), Term.of("c")))  // length 3
            .addDocument(1, List.of(Term.of("a")))                                // length 1
            .build();
        assertThat(idx.bm25Stats().avgDocLength()).isEqualTo(2.0);
    }
}
