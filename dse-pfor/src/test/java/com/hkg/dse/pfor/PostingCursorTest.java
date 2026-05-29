package com.hkg.dse.pfor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostingCursorTest {

    private PostingList build(int... docIds) {
        Bm25Stats stats = Bm25Stats.withDefaults(100.0);
        var builder = new PostingList.Builder(stats, 1_000_000, docIds.length);
        for (int d : docIds) {
            builder.add(d, 1, 100);
        }
        return builder.build();
    }

    @Test
    void emptyListExhaustedImmediately() {
        Bm25Stats stats = Bm25Stats.withDefaults(100.0);
        PostingList list = new PostingList.Builder(stats, 1_000_000, 0).build();
        PostingCursor cursor = new PostingCursor(list);
        assertThat(cursor.exhausted()).isTrue();
        assertThat(cursor.docId()).isEqualTo(PostingCursor.NO_MORE_DOCS);
    }

    @Test
    void iteratesAllDocs() {
        PostingList list = build(1, 5, 9, 12);
        PostingCursor cursor = new PostingCursor(list);
        assertThat(cursor.docId()).isEqualTo(1);
        cursor.next();
        assertThat(cursor.docId()).isEqualTo(5);
        cursor.next();
        assertThat(cursor.docId()).isEqualTo(9);
        cursor.next();
        assertThat(cursor.docId()).isEqualTo(12);
        cursor.next();
        assertThat(cursor.exhausted()).isTrue();
    }

    @Test
    void advanceSkipsToTarget() {
        PostingList list = build(1, 5, 9, 12, 20);
        PostingCursor cursor = new PostingCursor(list);
        cursor.advance(10);
        assertThat(cursor.docId()).isEqualTo(12);
        cursor.advance(15);
        assertThat(cursor.docId()).isEqualTo(20);
    }

    @Test
    void advanceBeyondLastDocExhausts() {
        PostingList list = build(1, 5, 9);
        PostingCursor cursor = new PostingCursor(list);
        cursor.advance(100);
        assertThat(cursor.exhausted()).isTrue();
    }

    @Test
    void advanceCrossesBlockBoundary() {
        // Build ~200 docs so we have ≥2 blocks
        int[] ids = new int[200];
        for (int i = 0; i < ids.length; i++) ids[i] = i * 2;  // 0, 2, 4, ..., 398
        PostingList list = build(ids);
        assertThat(list.blocks().size()).isGreaterThan(1);

        PostingCursor cursor = new PostingCursor(list);
        cursor.advance(300);
        assertThat(cursor.docId()).isEqualTo(300);
    }

    @Test
    void advancePastBlockSkipsWholeBlock() {
        int[] ids = new int[300];
        for (int i = 0; i < ids.length; i++) ids[i] = i;
        PostingList list = build(ids);

        PostingCursor cursor = new PostingCursor(list);
        // We are at doc 0; advancing past the first block should land us in block 2.
        cursor.advancePastBlock();
        assertThat(cursor.docId()).isEqualTo(128);
    }

    @Test
    void blockMaxDocIdReflectsCurrentBlock() {
        PostingList list = build(1, 5, 9);
        PostingCursor cursor = new PostingCursor(list);
        assertThat(cursor.blockMaxDocId()).isEqualTo(9);
    }
}
