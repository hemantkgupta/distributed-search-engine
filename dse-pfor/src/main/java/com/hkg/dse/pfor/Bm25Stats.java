package com.hkg.dse.pfor;

/**
 * Shared BM25 scoring parameters and computations.
 *
 * <p>Stores per-shard corpus statistics needed to compute BM25 impact
 * for posting-list entries: average document length, and the {@code k1}
 * and {@code b} tuning knobs.</p>
 *
 * <p>The standard defaults — {@code k1 = 1.2}, {@code b = 0.75} —
 * generalise tolerably well across corpora and are why BM25 has been
 * the production lexical baseline for 30 years. See concepts/bm25.md.</p>
 */
public record Bm25Stats(double k1, double b, double avgDocLength) {

    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    public Bm25Stats {
        if (k1 <= 0) throw new IllegalArgumentException("k1 must be positive");
        if (b < 0 || b > 1) throw new IllegalArgumentException("b must be in [0, 1]");
        if (avgDocLength <= 0) throw new IllegalArgumentException("avgDocLength must be positive");
    }

    public static Bm25Stats withDefaults(double avgDocLength) {
        return new Bm25Stats(DEFAULT_K1, DEFAULT_B, avgDocLength);
    }

    /**
     * Inverse document frequency:
     * {@code IDF(t) = log((N - df + 0.5) / (df + 0.5) + 1)}.
     */
    public double idf(int totalDocsInCorpus, int docFrequency) {
        double n = totalDocsInCorpus;
        double df = docFrequency;
        return Math.log((n - df + 0.5) / (df + 0.5) + 1.0);
    }

    /**
     * BM25 per-doc contribution for a single (term, doc) pair:
     * <pre>
     *   IDF(t) * (tf * (k1 + 1)) / (tf + k1 * (1 - b + b * |d| / avgdl))
     * </pre>
     */
    public double bm25(double idf, int termFrequency, int docLength) {
        double tf = termFrequency;
        double numerator = tf * (k1 + 1);
        double denominator = tf + k1 * (1.0 - b + b * (docLength / avgDocLength));
        return idf * (numerator / denominator);
    }
}
