package com.hkg.dse.tier;

import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;
import com.hkg.dse.shard.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TierClassifierTest {

    private Query query(int topK, int termCount) {
        List<Term> terms = new java.util.ArrayList<>();
        for (int i = 0; i < termCount; i++) terms.add(Term.of("t" + i));
        return new Query(
            terms,
            new float[]{1f, 0f},
            new float[][]{{1f, 0f}},
            new Principal("u", "t", Set.of()),
            topK
        );
    }

    @Test
    void shortHotQueryStaysOnTier1() {
        TierClassifier c = new TierClassifier();
        assertThat(c.classify(query(10, 2))).isEqualTo(TierClassifier.TierDecision.TIER1_ONLY);
    }

    @Test
    void deepRecallEscalatesToBoth() {
        TierClassifier c = new TierClassifier();
        assertThat(c.classify(query(100, 2))).isEqualTo(TierClassifier.TierDecision.BOTH);
    }

    @Test
    void manyTermsEscalatesToBoth() {
        TierClassifier c = new TierClassifier();
        assertThat(c.classify(query(10, 5))).isEqualTo(TierClassifier.TierDecision.BOTH);
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThatThrownBy(() -> new TierClassifier(0, 4))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TierClassifier(50, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
