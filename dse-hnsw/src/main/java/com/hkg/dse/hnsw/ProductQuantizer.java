package com.hkg.dse.hnsw;

/**
 * Simplified Product Quantization (PQ) — splits a D-dim vector into M
 * equal sub-vectors, learns a per-sub-space codebook of K centroids,
 * and replaces each sub-vector with its centroid ID.
 *
 * <p>For {@code D=64, M=8, K=16}, each vector compresses from 256 bytes
 * (64 floats) to 8 bytes (8 × 1-byte centroid IDs) — 32× compression.
 * Production PQ32 with D=768, M=32, K=256 hits 96×.</p>
 *
 * <p>Asymmetric Distance Computation (ADC) precomputes a per-query
 * lookup table of distances from each query sub-vector to each codebook
 * centroid, then approximates distance with M lookups + sum.</p>
 */
public final class ProductQuantizer {

    public static final int DEFAULT_M = 8;
    public static final int DEFAULT_K = 16;

    private final int dim;
    private final int m;
    private final int k;
    private final int subDim;
    /** centroids[m][k][subDim] */
    private final float[][][] codebooks;

    public ProductQuantizer(int dim, int m, int k, float[][][] codebooks) {
        if (dim % m != 0) {
            throw new IllegalArgumentException("dim must be divisible by m");
        }
        if (k < 1 || k > 256) {
            throw new IllegalArgumentException("k must be in [1, 256]");
        }
        this.dim = dim;
        this.m = m;
        this.k = k;
        this.subDim = dim / m;
        this.codebooks = codebooks;
    }

    /** Encode a full-precision vector into M centroid IDs (one per sub-space). */
    public byte[] encode(float[] vector) {
        if (vector.length != dim) {
            throw new IllegalArgumentException("vector dim mismatch: " + vector.length + " vs " + dim);
        }
        byte[] codes = new byte[m];
        for (int mi = 0; mi < m; mi++) {
            int bestK = 0;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int ki = 0; ki < k; ki++) {
                double d = 0;
                int base = mi * subDim;
                for (int i = 0; i < subDim; i++) {
                    double diff = vector[base + i] - codebooks[mi][ki][i];
                    d += diff * diff;
                }
                if (d < bestDist) {
                    bestDist = d;
                    bestK = ki;
                }
            }
            codes[mi] = (byte) bestK;
        }
        return codes;
    }

    /**
     * Precompute the per-query lookup table for ADC.
     * {@code lut[m][k] = ||q_m - C_m[k]||²}.
     */
    public float[][] precomputeLut(float[] queryVector) {
        if (queryVector.length != dim) {
            throw new IllegalArgumentException("query vector dim mismatch");
        }
        float[][] lut = new float[m][k];
        for (int mi = 0; mi < m; mi++) {
            int base = mi * subDim;
            for (int ki = 0; ki < k; ki++) {
                double d = 0;
                for (int i = 0; i < subDim; i++) {
                    double diff = queryVector[base + i] - codebooks[mi][ki][i];
                    d += diff * diff;
                }
                lut[mi][ki] = (float) d;
            }
        }
        return lut;
    }

    /**
     * ADC approximate squared distance from query to compressed vector.
     * {@code Σ_m LUT[m][codes[m]]}.
     */
    public float approxSquaredDistance(float[][] lut, byte[] codes) {
        if (codes.length != m) {
            throw new IllegalArgumentException("codes length mismatch");
        }
        float d = 0;
        for (int mi = 0; mi < m; mi++) {
            int ki = codes[mi] & 0xFF;
            d += lut[mi][ki];
        }
        return d;
    }

    public int dim() { return dim; }
    public int m() { return m; }
    public int k() { return k; }

    /**
     * Build a PQ trained by simple sampling — for each sub-space, pick K
     * random vectors from {@code training} as centroids. Production uses
     * k-means; this is a pedagogical stand-in that still demonstrates ADC.
     */
    public static ProductQuantizer trainSampled(float[][] training, int dim, int m, int k, long seed) {
        if (training.length < k) {
            throw new IllegalArgumentException("training set must have >= k vectors");
        }
        int subDim = dim / m;
        float[][][] codebooks = new float[m][k][subDim];
        java.util.Random rng = new java.util.Random(seed);
        for (int mi = 0; mi < m; mi++) {
            int[] picks = rng.ints(0, training.length).distinct().limit(k).toArray();
            int base = mi * subDim;
            for (int ki = 0; ki < k; ki++) {
                for (int i = 0; i < subDim; i++) {
                    codebooks[mi][ki][i] = training[picks[ki]][base + i];
                }
            }
        }
        return new ProductQuantizer(dim, m, k, codebooks);
    }
}
