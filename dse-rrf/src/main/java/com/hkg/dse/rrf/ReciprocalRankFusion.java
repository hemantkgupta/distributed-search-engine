package com.hkg.dse.rrf;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Score;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (Cormack, Clarke & Büttcher, 2009).
 *
 * <p>For each candidate doc, its RRF score is the sum across input lists
 * of {@code 1 / (k + rank)} with {@code k = 60} (the field-default
 * constant). RRF is score-agnostic — it uses only rank position — which
 * makes it robust to the dramatically different score distributions of
 * BM25 (unbounded positive) and cosine similarity (bounded [-1, 1]).</p>
 *
 * <p>Documents missing from a channel contribute 0 from that channel.
 * Channels with more candidates do not get an artificial RRF boost
 * because the contribution at deeper ranks shrinks.</p>
 *
 * <p>See {@code concepts/reciprocal-rank-fusion.md}.</p>
 */
public final class ReciprocalRankFusion {

    /** The k constant from the original paper. Hold this until evidence justifies change. */
    public static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusion() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusion(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        this.k = k;
    }

    @SafeVarargs
    public final List<Score> fuse(int topK, List<Score>... channels) {
        Map<Integer, Double> rrfScore = new HashMap<>();
        for (List<Score> channel : channels) {
            for (int rank = 0; rank < channel.size(); rank++) {
                int docId = channel.get(rank).docId().value();
                double contribution = 1.0 / (k + (rank + 1));  // 1-indexed rank
                rrfScore.merge(docId, contribution, Double::sum);
            }
        }
        List<Score> all = new ArrayList<>();
        for (var entry : rrfScore.entrySet()) {
            all.add(new Score(DocId.of(entry.getKey()), entry.getValue()));
        }
        Collections.sort(all);
        return all.subList(0, Math.min(topK, all.size()));
    }
}
