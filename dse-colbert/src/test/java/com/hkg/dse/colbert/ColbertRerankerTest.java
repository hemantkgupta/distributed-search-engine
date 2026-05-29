package com.hkg.dse.colbert;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Score;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ColbertRerankerTest {

    @Test
    void docWithMatchingTokenScoresHigher() {
        // Doc 1: token [1,0]. Doc 2: token [0,1]. Query: token [1,0].
        // Doc 1 should score higher than doc 2.
        Map<Integer, float[][]> tokens = Map.of(
            1, new float[][]{{1f, 0f}},
            2, new float[][]{{0f, 1f}}
        );
        ColbertReranker rr = new ColbertReranker(tokens);
        float[][] query = {{1f, 0f}};
        List<Score> ranked = rr.rerank(List.of(DocId.of(1), DocId.of(2)), query, 2);
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).docId().value()).isEqualTo(1);
    }

    @Test
    void multipleQueryTokensSumIndependently() {
        Map<Integer, float[][]> tokens = Map.of(
            1, new float[][]{{1f, 0f}, {0f, 1f}}  // both bases present
        );
        ColbertReranker rr = new ColbertReranker(tokens);
        float[][] query = {{1f, 0f}, {0f, 1f}};
        List<Score> ranked = rr.rerank(List.of(DocId.of(1)), query, 1);
        // Each query token max-sims to 1.0; sum = 2.0
        assertThat(ranked.get(0).value()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void emptyDocTokensScoreZero() {
        Map<Integer, float[][]> tokens = Map.of(1, new float[0][]);
        ColbertReranker rr = new ColbertReranker(tokens);
        float[][] query = {{1f, 0f}};
        List<Score> ranked = rr.rerank(List.of(DocId.of(1)), query, 1);
        assertThat(ranked.get(0).value()).isEqualTo(0.0);
    }

    @Test
    void docNotInTokenStoreScoresZero() {
        ColbertReranker rr = new ColbertReranker(Map.of());
        List<Score> ranked = rr.rerank(List.of(DocId.of(42)), new float[][]{{1f}}, 1);
        assertThat(ranked.get(0).value()).isEqualTo(0.0);
    }
}
