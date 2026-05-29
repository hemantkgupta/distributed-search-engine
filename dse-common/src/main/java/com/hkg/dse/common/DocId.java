package com.hkg.dse.common;

/**
 * Document identifier within a single shard.
 *
 * <p>The doc_id is the join key across the inverted index, the HNSW graph,
 * the forward column store, and the tombstone bitmap inside a unified
 * segment. All four data structures index by the same 32-bit doc_id —
 * see {@code patterns/unified-lexical-vector-segment.md}.</p>
 *
 * <p>doc_ids are dense within a segment, monotonically assigned during
 * indexing, and never reused even when documents are tombstoned.</p>
 */
public record DocId(int value) implements Comparable<DocId> {

    public DocId {
        if (value < 0) {
            throw new IllegalArgumentException("DocId must be non-negative: " + value);
        }
    }

    @Override
    public int compareTo(DocId other) {
        return Integer.compare(this.value, other.value);
    }

    public static DocId of(int v) {
        return new DocId(v);
    }
}
