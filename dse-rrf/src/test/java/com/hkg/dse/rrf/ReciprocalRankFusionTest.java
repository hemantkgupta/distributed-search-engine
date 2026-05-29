package com.hkg.dse.rrf;

import com.hkg.dse.common.Score;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReciprocalRankFusionTest {

    @Test
    void fusionPrefersDocsTopInBothChannels() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        // Channel A: 1, 2, 3
        List<Score> chA = List.of(Score.of(1, 5.0), Score.of(2, 4.0), Score.of(3, 3.0));
        // Channel B: 2, 1, 4
        List<Score> chB = List.of(Score.of(2, 0.9), Score.of(1, 0.8), Score.of(4, 0.5));
        List<Score> fused = rrf.fuse(3, chA, chB);
        // Doc 1: rank 1 in A + rank 2 in B; doc 2: rank 2 in A + rank 1 in B.
        // Both get 1/(60+1) + 1/(60+2). They tie on score; Score's natural order
        // resolves the tie by doc-ID ASCENDING → doc 1 first, doc 2 second.
        // Doc 3 and 4 only appear in one channel each, lower contribution.
        assertThat(fused).hasSize(3);
        assertThat(fused).extracting(s -> s.docId().value())
            .startsWith(1, 2);
    }

    @Test
    void fusionScoreAgnosticToMagnitudes() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        // Two channels with wildly different score scales should fuse identically
        // when the rank-order is the same.
        List<Score> chBigScores = List.of(
            Score.of(1, 1_000_000.0), Score.of(2, 999_999.0), Score.of(3, 5.0)
        );
        List<Score> chTiny = List.of(
            Score.of(1, 0.001), Score.of(2, 0.0009), Score.of(3, 0.00005)
        );
        List<Score> fusedBig = rrf.fuse(3, chBigScores);
        List<Score> fusedTiny = rrf.fuse(3, chTiny);
        assertThat(fusedBig.get(0).docId()).isEqualTo(fusedTiny.get(0).docId());
        assertThat(fusedBig.get(0).value()).isCloseTo(fusedTiny.get(0).value(), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void docMissingFromChannelStillCountsViaOtherChannel() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        List<Score> chA = List.of(Score.of(1, 5.0));
        List<Score> chB = List.of(Score.of(2, 5.0));
        List<Score> fused = rrf.fuse(2, chA, chB);
        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).value()).isCloseTo(fused.get(1).value(), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void topKLimitsResults() {
        ReciprocalRankFusion rrf = new ReciprocalRankFusion();
        List<Score> chA = List.of(Score.of(1, 5), Score.of(2, 4), Score.of(3, 3), Score.of(4, 2));
        List<Score> fused = rrf.fuse(2, chA);
        assertThat(fused).hasSize(2);
    }

    @Test
    void rejectsInvalidK() {
        assertThatThrownBy(() -> new ReciprocalRankFusion(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
