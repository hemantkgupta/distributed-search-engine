package com.hkg.dse.segment;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedSegmentTest {

    private static final int DIM = 8;

    private float[] vec(long seed) {
        Random r = new Random(seed);
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) v[i] = (float) r.nextGaussian();
        return v;
    }

    private float[][] tokens(long seed) {
        Random r = new Random(seed);
        float[][] t = new float[3][DIM];
        for (int i = 0; i < t.length; i++) {
            for (int j = 0; j < DIM; j++) t[i][j] = (float) r.nextGaussian();
        }
        return t;
    }

    private UnifiedSegment buildSegment(int numDocs) {
        float[][] training = new float[numDocs][DIM];
        for (int i = 0; i < numDocs; i++) training[i] = vec(i);
        ProductQuantizer pq = ProductQuantizer.trainSampled(training, DIM, 2, 4, 1L);

        SegmentBuilder builder = new SegmentBuilder(pq, 4);
        for (int i = 0; i < numDocs; i++) {
            builder.addDocument(
                DocId.of(i),
                List.of(Term.of("cisco"), Term.of("doc" + i)),
                training[i],
                "payload" + i,
                tokens(i)
            );
        }
        return builder.build();
    }

    @Test
    void builderProducesCompleteSegment() {
        UnifiedSegment seg = buildSegment(20);
        assertThat(seg.id()).isNotNull();
        assertThat(seg.invertedIndex().totalDocs()).isEqualTo(20);
        assertThat(seg.hnswGraph().size()).isEqualTo(20);
        assertThat(seg.forwardStore()).hasSize(20);
        assertThat(seg.tombstones().isEmpty()).isTrue();
        assertThat(seg.liveDocCount()).isEqualTo(20);
    }

    @Test
    void tombstoneMarksDocAsNotLive() {
        UnifiedSegment seg = buildSegment(5);
        assertThat(seg.isLive(DocId.of(2))).isTrue();
        seg.markTombstone(DocId.of(2));
        assertThat(seg.isLive(DocId.of(2))).isFalse();
    }

    @Test
    void tombstoneRatioReflectsDeletes() {
        UnifiedSegment seg = buildSegment(10);
        seg.markTombstone(DocId.of(0));
        seg.markTombstone(DocId.of(1));
        assertThat(seg.tombstoneRatio()).isEqualTo(0.2);
    }
}
