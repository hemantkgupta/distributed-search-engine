package com.hkg.dse.merge;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.segment.SegmentBuilder;
import com.hkg.dse.segment.UnifiedSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TieredMergePolicyTest {

    private static final int DIM = 8;

    private UnifiedSegment buildSegment(int numDocs, long seed) {
        Random r = new Random(seed);
        float[][] training = new float[numDocs][DIM];
        for (int i = 0; i < numDocs; i++) {
            for (int j = 0; j < DIM; j++) training[i][j] = (float) r.nextGaussian();
        }
        ProductQuantizer pq = ProductQuantizer.trainSampled(training, DIM, 2, 4, seed);
        SegmentBuilder b = new SegmentBuilder(pq, 4);
        for (int i = 0; i < numDocs; i++) {
            b.addDocument(
                DocId.of(i + (int) (seed * 1000)),  // disjoint doc-IDs across segments
                List.of(Term.of("token")),
                training[i],
                "payload",
                new float[][]{{1f, 0f}}
            );
        }
        return b.build();
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new TieredMergePolicy(-0.1, 3))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TieredMergePolicy(0.5, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsEmptyWhenTooFewSegments() {
        TieredMergePolicy policy = new TieredMergePolicy();
        List<UnifiedSegment> segs = List.of(buildSegment(10, 1));
        assertThat(policy.pickMergeCandidates(segs)).isEmpty();
    }

    @Test
    void tierMergePicksMergeFactorSmallest() {
        TieredMergePolicy policy = new TieredMergePolicy(0.5, 3);
        List<UnifiedSegment> segs = new ArrayList<>();
        segs.add(buildSegment(10, 1));
        segs.add(buildSegment(20, 2));
        segs.add(buildSegment(30, 3));
        segs.add(buildSegment(40, 4));
        List<UnifiedSegment> picked = policy.pickMergeCandidates(segs);
        assertThat(picked).hasSize(3);
        // Smallest first
        for (UnifiedSegment s : picked) {
            assertThat(s.liveDocCount()).isLessThanOrEqualTo(30);
        }
    }

    @Test
    void forceMergeTriggersOnTombstoneThreshold() {
        TieredMergePolicy policy = new TieredMergePolicy(0.30, 3);
        UnifiedSegment victim = buildSegment(10, 1);
        // Tombstone 4 docs out of 10 = 40 % >= 30 % threshold
        for (int i = 0; i < 4; i++) victim.markTombstone(DocId.of(i + 1000));
        UnifiedSegment fine = buildSegment(100, 2);
        List<UnifiedSegment> segs = List.of(victim, fine);
        List<UnifiedSegment> picked = policy.pickMergeCandidates(segs);
        // Force-merge includes victim
        assertThat(picked).contains(victim);
    }
}
