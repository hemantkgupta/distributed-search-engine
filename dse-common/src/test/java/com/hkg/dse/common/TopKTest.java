package com.hkg.dse.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopKTest {

    @Test
    void emptyHeapHasNegativeInfinityFloor() {
        TopK heap = new TopK(3);
        assertThat(heap.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(heap.size()).isZero();
    }

    @Test
    void floorIsKthBestWhenFull() {
        TopK heap = new TopK(3);
        heap.offer(Score.of(1, 1.0));
        heap.offer(Score.of(2, 5.0));
        heap.offer(Score.of(3, 3.0));
        // Top-3 by score: 5, 3, 1 → floor = 1 (the K-th best)
        assertThat(heap.floor()).isEqualTo(1.0);
    }

    @Test
    void worseScoreDoesNotEvict() {
        TopK heap = new TopK(2);
        heap.offer(Score.of(1, 5.0));
        heap.offer(Score.of(2, 8.0));
        heap.offer(Score.of(3, 2.0));  // below floor; should be ignored
        assertThat(heap.size()).isEqualTo(2);
        assertThat(heap.sortedResults().get(0).value()).isEqualTo(8.0);
        assertThat(heap.sortedResults().get(1).value()).isEqualTo(5.0);
    }

    @Test
    void betterScoreEvictsCurrentWorst() {
        TopK heap = new TopK(2);
        heap.offer(Score.of(1, 5.0));
        heap.offer(Score.of(2, 3.0));
        heap.offer(Score.of(3, 10.0));  // evicts 3.0
        assertThat(heap.sortedResults().get(0).value()).isEqualTo(10.0);
        assertThat(heap.sortedResults().get(1).value()).isEqualTo(5.0);
    }

    @Test
    void rejectsKBelowOne() {
        try {
            new TopK(0);
            assertThat(false).isTrue();
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageContaining("k must be >= 1");
        }
    }
}
