package com.hkg.dse.node;

import com.hkg.dse.common.DocId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentRouterTest {

    @Test
    void allShardsAreInRange() {
        DocumentRouter r = new DocumentRouter(10);
        for (int i = 0; i < 1_000; i++) {
            int shard = r.shardFor(DocId.of(i));
            assertThat(shard).isBetween(0, 9);
        }
    }

    @Test
    void distributionIsReasonablyBalanced() {
        DocumentRouter r = new DocumentRouter(8);
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            counts.merge(r.shardFor(DocId.of(i)), 1, Integer::sum);
        }
        // Each shard should hold roughly 1250 docs (10000 / 8). Allow 30% slack
        // for hash variance over a small corpus.
        for (int count : counts.values()) {
            assertThat(count).isBetween(875, 1625);
        }
    }

    @Test
    void rejectsZeroShardCount() {
        assertThatThrownBy(() -> new DocumentRouter(0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
