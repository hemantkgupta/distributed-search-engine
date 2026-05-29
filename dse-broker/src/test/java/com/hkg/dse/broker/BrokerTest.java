package com.hkg.dse.broker;

import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Principal;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.segment.SegmentBuilder;
import com.hkg.dse.segment.UnifiedSegment;
import com.hkg.dse.shard.ContentNode;
import com.hkg.dse.shard.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerTest {

    private static final int DIM = 8;
    private Broker broker;

    @AfterEach
    void tearDown() {
        if (broker != null) broker.shutdown();
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

    private UnifiedSegment buildSegment(int baseDocId, int numDocs) {
        float[][] training = new float[numDocs][DIM];
        for (int i = 0; i < numDocs; i++) training[i] = vec(baseDocId + i);
        ProductQuantizer pq = ProductQuantizer.trainSampled(training, DIM, 2, 4, 1L);
        SegmentBuilder b = new SegmentBuilder(pq, 4);
        for (int i = 0; i < numDocs; i++) {
            b.addDocument(
                DocId.of(baseDocId + i),
                List.of(Term.of("cisco"), Term.aclRead("user_1")),
                training[i],
                "payload",
                tokens(baseDocId + i + 100)
            );
        }
        return b.build();
    }

    @Test
    void brokerFansOutToAllShardsAndMergesResults() {
        List<ContentNode> shards = new ArrayList<>();
        shards.add(new ContentNode(buildSegment(0, 10)));
        shards.add(new ContentNode(buildSegment(100, 10)));
        shards.add(new ContentNode(buildSegment(200, 10)));
        broker = new Broker(shards);

        Principal principal = new Principal("1", "t", Set.of());
        Query query = new Query(
            List.of(Term.of("cisco")),
            vec(50),
            tokens(50),
            principal,
            5
        );
        BrokerResult result = broker.search(query);
        assertThat(result.partial()).isFalse();
        assertThat(result.topK()).isNotEmpty();
        assertThat(result.topK().size()).isLessThanOrEqualTo(5);
    }

    @Test
    void emptyShardListReturnsEmptyResult() {
        broker = new Broker(List.of());
        Principal principal = new Principal("1", "t", Set.of());
        Query query = new Query(
            List.of(Term.of("cisco")),
            vec(1),
            tokens(1),
            principal,
            5
        );
        BrokerResult result = broker.search(query);
        assertThat(result.topK()).isEmpty();
    }
}
