package com.hkg.dse.common;

/**
 * A scored doc-id pair, used uniformly as the candidate-set entry across
 * lexical retrieval, vector retrieval, RRF fusion, and reranking.
 *
 * <p>Higher score = more relevant. The score itself is opaque to fusion —
 * RRF uses only rank position, not absolute score — but is meaningful
 * within a single channel for top-K maintenance.</p>
 */
public record Score(DocId docId, double value) implements Comparable<Score> {

    /** Sort by score descending; doc_id ascending as tiebreaker for determinism. */
    @Override
    public int compareTo(Score other) {
        int byScore = Double.compare(other.value, this.value);
        if (byScore != 0) return byScore;
        return Integer.compare(this.docId.value(), other.docId.value());
    }

    public static Score of(int docId, double value) {
        return new Score(DocId.of(docId), value);
    }
}
