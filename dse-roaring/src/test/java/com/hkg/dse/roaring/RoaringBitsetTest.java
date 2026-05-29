package com.hkg.dse.roaring;

import com.hkg.dse.common.DocId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoaringBitsetTest {

    @Test
    void containsAddedValues() {
        RoaringBitset b = new RoaringBitset();
        b.add(0);
        b.add(42);
        b.add(100_000);
        assertThat(b.contains(0)).isTrue();
        assertThat(b.contains(42)).isTrue();
        assertThat(b.contains(100_000)).isTrue();
        assertThat(b.contains(43)).isFalse();
        assertThat(b.cardinality()).isEqualTo(3);
    }

    @Test
    void addDocIdRoundTrips() {
        RoaringBitset b = new RoaringBitset();
        b.add(DocId.of(7));
        assertThat(b.contains(DocId.of(7))).isTrue();
        assertThat(b.contains(DocId.of(8))).isFalse();
    }

    @Test
    void duplicateAddDoesNotInflateCardinality() {
        RoaringBitset b = new RoaringBitset();
        b.add(5);
        b.add(5);
        assertThat(b.cardinality()).isEqualTo(1);
    }

    @Test
    void rejectsNegativeAdd() {
        RoaringBitset b = new RoaringBitset();
        assertThatThrownBy(() -> b.add(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsRejectsNegative() {
        RoaringBitset b = new RoaringBitset();
        assertThat(b.contains(-1)).isFalse();
    }

    @Test
    void orUnionsSets() {
        RoaringBitset a = new RoaringBitset();
        a.add(1); a.add(2);
        RoaringBitset b = new RoaringBitset();
        b.add(2); b.add(3);
        a.or(b);
        assertThat(a.contains(1)).isTrue();
        assertThat(a.contains(2)).isTrue();
        assertThat(a.contains(3)).isTrue();
        assertThat(a.cardinality()).isEqualTo(3);
    }

    @Test
    void andIntersectsSets() {
        RoaringBitset a = new RoaringBitset();
        a.add(1); a.add(2); a.add(3);
        RoaringBitset b = new RoaringBitset();
        b.add(2); b.add(3); b.add(4);
        a.and(b);
        assertThat(a.contains(1)).isFalse();
        assertThat(a.contains(2)).isTrue();
        assertThat(a.contains(3)).isTrue();
        assertThat(a.contains(4)).isFalse();
        assertThat(a.cardinality()).isEqualTo(2);
    }

    @Test
    void andNotRemovesIntersection() {
        RoaringBitset a = new RoaringBitset();
        a.add(1); a.add(2); a.add(3);
        RoaringBitset tombstones = new RoaringBitset();
        tombstones.add(2);
        a.andNot(tombstones);
        assertThat(a.contains(1)).isTrue();
        assertThat(a.contains(2)).isFalse();
        assertThat(a.contains(3)).isTrue();
        assertThat(a.cardinality()).isEqualTo(2);
    }

    @Test
    void iteratorReturnsAscendingOrder() {
        RoaringBitset b = new RoaringBitset();
        int[] inserts = {500_000, 42, 0, 100, 65_536, 999_999};
        for (int v : inserts) b.add(v);
        List<Integer> out = new ArrayList<>();
        b.iterator().forEachRemaining(out::add);
        assertThat(out).containsExactly(0, 42, 100, 65_536, 500_000, 999_999);
    }

    @Test
    void crossChunkBoundary() {
        RoaringBitset b = new RoaringBitset();
        b.add(65_535);  // last value of chunk 0
        b.add(65_536);  // first value of chunk 1
        assertThat(b.contains(65_535)).isTrue();
        assertThat(b.contains(65_536)).isTrue();
        assertThat(b.cardinality()).isEqualTo(2);
    }

    @Test
    void emptyBitsetEdgeCases() {
        RoaringBitset b = new RoaringBitset();
        assertThat(b.isEmpty()).isTrue();
        assertThat(b.iterator().hasNext()).isFalse();
    }
}
