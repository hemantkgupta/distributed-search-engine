package com.hkg.dse.pfor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingListTest {

    @Test
    void buildsMultiBlockList() {
        Bm25Stats stats = Bm25Stats.withDefaults(100.0);
        var builder = new PostingList.Builder(stats, 1_000_000, 1_000);
        for (int i = 0; i < 300; i++) {
            builder.add(i, 1 + i % 5, 100);
        }
        PostingList list = builder.build();
        assertThat(list.totalSize()).isEqualTo(300);
        // 300 docs / 128 block size = 3 blocks (last is partial)
        assertThat(list.blocks()).hasSize(3);
        assertThat(list.blocks().get(0).size()).isEqualTo(128);
        assertThat(list.blocks().get(1).size()).isEqualTo(128);
        assertThat(list.blocks().get(2).size()).isEqualTo(44);
    }

    @Test
    void rejectsOutOfOrderInserts() {
        Bm25Stats stats = Bm25Stats.withDefaults(100.0);
        var builder = new PostingList.Builder(stats, 1_000_000, 1_000);
        builder.add(10, 1, 100);
        assertThatThrownBy(() -> builder.add(10, 1, 100))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.add(5, 1, 100))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxImpactBoundsBlockEntries() {
        Bm25Stats stats = Bm25Stats.withDefaults(100.0);
        var builder = new PostingList.Builder(stats, 1_000_000, 1_000);
        // Varied TFs so some docs score higher than others
        for (int i = 0; i < 128; i++) {
            builder.add(i, 1 + i % 10, 100);
        }
        PostingList list = builder.build();
        PostingBlock block = list.blocks().get(0);
        for (float tfImpact : block.tfImpacts()) {
            assertThat(tfImpact).isLessThanOrEqualTo(block.maxImpact() + 1e-6f);
        }
    }
}
