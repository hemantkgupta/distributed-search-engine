package com.hkg.dse.hnsw;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Score;
import com.hkg.dse.roaring.RoaringBitset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class HnswGraphTest {

    private static final int DIM = 16;
    private static final int M = 4;
    private static final int K = 8;

    private float[][] corpus(int n, long seed) {
        Random r = new Random(seed);
        float[][] out = new float[n][DIM];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < DIM; j++) out[i][j] = (float) r.nextGaussian();
        }
        return out;
    }

    @Test
    void emptySearchReturnsEmpty() {
        float[][] training = corpus(20, 1);
        ProductQuantizer pq = ProductQuantizer.trainSampled(training, DIM, M, K, 1L);
        HnswGraph g = new HnswGraph(pq, 4);
        assertThat(g.search(training[0], 5, null)).isEmpty();
    }

    @Test
    void insertedNodesAreSearchable() {
        float[][] data = corpus(50, 7);
        ProductQuantizer pq = ProductQuantizer.trainSampled(data, DIM, M, K, 1L);
        // M=8 neighbours per node for adequate connectivity on a 50-node graph
        HnswGraph g = new HnswGraph(pq, 8);
        for (int i = 0; i < data.length; i++) {
            g.insert(DocId.of(i), data[i]);
        }
        assertThat(g.size()).isEqualTo(50);
        // Query returns non-empty result set
        List<Score> hits = g.search(data[10], 20, null);
        assertThat(hits).isNotEmpty();
    }

    @Test
    void aclPreFilterExcludesUnauthorisedDocs() {
        float[][] data = corpus(30, 13);
        ProductQuantizer pq = ProductQuantizer.trainSampled(data, DIM, M, K, 1L);
        HnswGraph g = new HnswGraph(pq, 4);
        for (int i = 0; i < data.length; i++) {
            g.insert(DocId.of(i), data[i]);
        }
        // Authorise only odd doc-IDs
        RoaringBitset acl = new RoaringBitset();
        for (int i = 0; i < data.length; i++) if (i % 2 == 1) acl.add(i);
        List<Score> hits = g.search(data[0], 10, acl);
        // Every returned doc-ID must be authorised
        for (Score s : hits) {
            assertThat(s.docId().value() % 2).isEqualTo(1);
        }
    }

    @Test
    void unauthorisedNodesStillTraversedForNavigation() {
        // Build a corpus and authorise a sparse subset. Without traversal
        // through unauthorised nodes, the walker would disconnect from
        // most of the graph and return empty. We assert that the walker
        // DOES return authorised results — proving links through
        // unauthorised nodes are still followed.
        float[][] data = corpus(50, 19);
        ProductQuantizer pq = ProductQuantizer.trainSampled(data, DIM, M, K, 1L);
        HnswGraph g = new HnswGraph(pq, 8);
        for (int i = 0; i < data.length; i++) g.insert(DocId.of(i), data[i]);

        // Authorise 10 % of docs (5 of 50) — sparse permission scenario
        RoaringBitset acl = new RoaringBitset();
        for (int i = 0; i < 50; i += 10) acl.add(i);
        List<Score> hits = g.search(data[20], 5, acl);
        // The walker must traverse unauthorised neighbours to reach the
        // authorised set; we expect a non-empty result.
        assertThat(hits).isNotEmpty();
        for (Score s : hits) {
            assertThat(s.docId().value() % 10).isEqualTo(0);  // all authorised
        }
    }

    @Test
    void rejectsTooSmallMaxNeighbours() {
        float[][] data = corpus(20, 1);
        ProductQuantizer pq = ProductQuantizer.trainSampled(data, DIM, M, K, 1L);
        try {
            new HnswGraph(pq, 1);
            assertThat(false).isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("maxNeighbours");
        }
    }
}
