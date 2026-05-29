package com.hkg.dse.node;

import com.hkg.dse.broker.BrokerResult;
import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.indexer.DocumentEvent;
import com.hkg.dse.shard.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchClusterIntegrationTest {

    private static final int DIM = 8;
    private SearchCluster cluster;

    @AfterEach
    void tearDown() {
        if (cluster != null) cluster.shutdown();
    }

    private float[] vec(long seed) {
        Random r = new Random(seed);
        float[] v = new float[DIM];
        for (int i = 0; i < DIM; i++) v[i] = (float) r.nextGaussian();
        return v;
    }

    private float[][] tokens(long seed) {
        Random r = new Random(seed);
        float[][] t = new float[2][DIM];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < DIM; j++) t[i][j] = (float) r.nextGaussian();
        }
        return t;
    }

    private ProductQuantizer pq(int n) {
        float[][] training = new float[n][DIM];
        for (int i = 0; i < n; i++) training[i] = vec(i);
        return ProductQuantizer.trainSampled(training, DIM, 2, 4, 1L);
    }

    @Test
    void documentsRouteToShardsAndAreSearchable() {
        cluster = new SearchCluster(4, pq(60), 4, 100);
        // Ingest 60 docs — all accessible to user_42
        for (int i = 0; i < 60; i++) {
            cluster.ingest(new DocumentEvent(
                DocId.of(i),
                List.of(Term.of("cisco")),
                List.of("user_42"),
                "payload" + i,
                vec(i),
                tokens(i + 1000)
            ));
        }
        cluster.commitAll();
        assertThat(cluster.liveSegmentCount()).isLessThanOrEqualTo(4);
        // Search should fan out and find authorised docs
        BrokerResult result = cluster.search(new Query(
            List.of(Term.of("cisco")),
            vec(0),
            tokens(0),
            new Principal("42", "t", Set.of()),
            10
        ));
        assertThat(result.topK()).isNotEmpty();
    }

    @Test
    void crossShardAclEnforcedConsistently() {
        cluster = new SearchCluster(3, pq(30), 4, 100);
        // Even doc-IDs accessible to user_1; odd to user_2
        for (int i = 0; i < 30; i++) {
            String aclUser = (i % 2 == 0) ? "user_1" : "user_2";
            cluster.ingest(new DocumentEvent(
                DocId.of(i),
                List.of(Term.of("doc")),
                List.of(aclUser),
                "p" + i,
                vec(i),
                tokens(i + 200)
            ));
        }
        cluster.commitAll();
        // Query as user_1 — should see only even doc-IDs
        BrokerResult result = cluster.search(new Query(
            List.of(Term.of("doc")),
            vec(0),
            tokens(0),
            new Principal("1", "t", Set.of()),
            20
        ));
        for (var score : result.topK()) {
            assertThat(score.docId().value() % 2).isEqualTo(0);
        }
    }

    @Test
    void tombstonedDocsExcludedAcrossCluster() {
        cluster = new SearchCluster(2, pq(20), 4, 100);
        for (int i = 0; i < 20; i++) {
            cluster.ingest(new DocumentEvent(
                DocId.of(i),
                List.of(Term.of("hello")),
                List.of("user_42"),
                "p" + i,
                vec(i),
                tokens(i + 300)
            ));
        }
        cluster.commitAll();
        for (int i = 0; i < 20; i++) cluster.tombstone(DocId.of(i));
        BrokerResult result = cluster.search(new Query(
            List.of(Term.of("hello")),
            vec(0),
            tokens(0),
            new Principal("42", "t", Set.of()),
            10
        ));
        assertThat(result.topK()).isEmpty();
    }
}
