package com.hkg.dse.hnsw;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductQuantizerTest {

    private float[][] randomCorpus(int n, int dim, long seed) {
        Random r = new Random(seed);
        float[][] out = new float[n][dim];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < dim; j++) {
                out[i][j] = (float) r.nextGaussian();
            }
        }
        return out;
    }

    @Test
    void encodeProducesMBytesPerVector() {
        int dim = 64, m = 8, k = 16;
        float[][] corpus = randomCorpus(50, dim, 42);
        ProductQuantizer pq = ProductQuantizer.trainSampled(corpus, dim, m, k, 1L);
        byte[] code = pq.encode(corpus[0]);
        assertThat(code).hasSize(m);
    }

    @Test
    void encodingIsDeterministic() {
        int dim = 32, m = 4, k = 8;
        float[][] corpus = randomCorpus(50, dim, 99);
        ProductQuantizer pq = ProductQuantizer.trainSampled(corpus, dim, m, k, 1L);
        byte[] a = pq.encode(corpus[7]);
        byte[] b = pq.encode(corpus[7]);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void adcDistanceTracksTrueDistance() {
        int dim = 32, m = 4, k = 8;
        float[][] corpus = randomCorpus(100, dim, 7);
        ProductQuantizer pq = ProductQuantizer.trainSampled(corpus, dim, m, k, 1L);
        float[] query = corpus[0];
        float[][] lut = pq.precomputeLut(query);
        // query-to-query approx distance should be 0 (or near-0)
        byte[] selfCode = pq.encode(query);
        float selfDist = pq.approxSquaredDistance(lut, selfCode);
        // and should be small relative to a random-pair distance
        byte[] otherCode = pq.encode(corpus[50]);
        float otherDist = pq.approxSquaredDistance(lut, otherCode);
        assertThat(selfDist).isLessThan(otherDist);
    }

    @Test
    void rejectsDimMismatch() {
        int dim = 32, m = 4, k = 8;
        float[][] corpus = randomCorpus(50, dim, 42);
        ProductQuantizer pq = ProductQuantizer.trainSampled(corpus, dim, m, k, 1L);
        float[] wrongDim = new float[20];
        assertThatThrownBy(() -> pq.encode(wrongDim))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDimNotDivisibleByM() {
        float[][] corpus = randomCorpus(50, 32, 42);
        assertThatThrownBy(() -> ProductQuantizer.trainSampled(corpus, 32, 5, 8, 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
