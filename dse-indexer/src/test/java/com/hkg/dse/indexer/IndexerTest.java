package com.hkg.dse.indexer;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.segment.UnifiedSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexerTest {

    private static final int DIM = 8;

    private float[] vec(long seed) {
        Random r = new Random(seed);
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) v[i] = (float) r.nextGaussian();
        return v;
    }

    private float[][] tokens(long seed) {
        Random r = new Random(seed);
        float[][] t = new float[2][DIM];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < DIM; j++) t[i][j] = (float) r.nextGaussian();
        }
        return t;
    }

    private ProductQuantizer pq() {
        float[][] training = new float[20][DIM];
        for (int i = 0; i < 20; i++) training[i] = vec(i);
        return ProductQuantizer.trainSampled(training, DIM, 2, 4, 1L);
    }

    @Test
    void acceptReturnsTrueWhenThresholdReached() {
        Indexer indexer = new Indexer(pq(), 4, 3);
        assertThat(indexer.accept(event(0))).isFalse();
        assertThat(indexer.accept(event(1))).isFalse();
        assertThat(indexer.accept(event(2))).isTrue();  // 3rd doc → flush
    }

    @Test
    void flushProducesSegmentWithAllPendingDocs() {
        Indexer indexer = new Indexer(pq(), 4, 5);
        for (int i = 0; i < 4; i++) indexer.accept(event(i));
        UnifiedSegment seg = indexer.flush();
        assertThat(seg.liveDocCount()).isEqualTo(4);
        assertThat(seg.invertedIndex().totalDocs()).isEqualTo(4);
        assertThat(indexer.isSealed()).isTrue();
    }

    @Test
    void cannotAcceptAfterFlush() {
        Indexer indexer = new Indexer(pq(), 4, 5);
        indexer.accept(event(0));
        indexer.flush();
        assertThatThrownBy(() -> indexer.accept(event(1)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aclTokensFromEventBecomeAclTerms() {
        Indexer indexer = new Indexer(pq(), 4, 5);
        DocumentEvent event = new DocumentEvent(
            DocId.of(7),
            List.of(Term.of("hello")),
            List.of("user_42", "group_admins"),
            "payload",
            vec(7),
            tokens(7)
        );
        indexer.accept(event);
        UnifiedSegment seg = indexer.flush();
        // ACL terms should be queryable via the inverted index
        assertThat(seg.invertedIndex().postingsFor(Term.aclRead("user_42"))).isPresent();
        assertThat(seg.invertedIndex().postingsFor(Term.aclRead("group_admins"))).isPresent();
    }

    private DocumentEvent event(int id) {
        return new DocumentEvent(
            DocId.of(id),
            List.of(Term.of("content")),
            List.of("user_default"),
            "payload" + id,
            vec(id),
            tokens(id)
        );
    }
}
