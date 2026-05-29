package com.hkg.dse.colbert;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Score;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simplified ColBERT-style MaxSim reranker.
 *
 * <p>For each candidate doc, compute
 * {@code score(q, d) = Σ_i max_j (q_i · d_j)} — for each query token,
 * find the best-matching doc token by dot product; sum those maxes.</p>
 *
 * <p>The "late interaction" name is because the cross-token interaction
 * is deferred to a single lightweight aggregation rather than fed
 * through a full transformer (cross-encoder). Doc token vectors are
 * pre-computed and stored at index time; only the query needs encoding
 * at query time.</p>
 *
 * <p>Production ColBERT uses 128-dim vectors per token. This module
 * accepts arbitrary dimensions for pedagogical flexibility.</p>
 */
public final class ColbertReranker {

    /** docId -> per-token vectors (each float[]). Token store. */
    private final Map<Integer, float[][]> docTokens;

    public ColbertReranker(Map<Integer, float[][]> docTokens) {
        this.docTokens = Map.copyOf(docTokens);
    }

    /**
     * Rerank a candidate set by ColBERT MaxSim against the query's
     * per-token vectors.
     *
     * @param candidateDocs ranked candidate set from the first stage
     * @param queryTokens   per-token query vectors
     * @param topK          top-K to keep
     */
    public List<Score> rerank(List<DocId> candidateDocs, float[][] queryTokens, int topK) {
        List<Score> scored = new ArrayList<>();
        for (DocId docId : candidateDocs) {
            float[][] tokens = docTokens.get(docId.value());
            if (tokens == null || tokens.length == 0) {
                scored.add(new Score(docId, 0.0));
                continue;
            }
            double sum = 0;
            for (float[] qTok : queryTokens) {
                double bestSim = Double.NEGATIVE_INFINITY;
                for (float[] dTok : tokens) {
                    double sim = dot(qTok, dTok);
                    if (sim > bestSim) bestSim = sim;
                }
                sum += bestSim;
            }
            scored.add(new Score(docId, sum));
        }
        Collections.sort(scored);
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    private static double dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("token dim mismatch: " + a.length + " vs " + b.length);
        }
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
