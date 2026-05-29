package com.hkg.dse.shard;

import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;

import java.util.List;

/**
 * A search query carrying the lexical terms, the dense query vector,
 * the per-token query vectors for ColBERT, the authenticated principal,
 * and the requested top-K.
 */
public record Query(
    List<Term> lexicalTerms,
    float[] denseVector,
    float[][] colbertTokens,
    Principal principal,
    int topK
) {
    public Query {
        if (lexicalTerms == null) throw new IllegalArgumentException("lexicalTerms required");
        if (denseVector == null) throw new IllegalArgumentException("denseVector required");
        if (principal == null) throw new IllegalArgumentException("principal required");
        if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
    }
}
