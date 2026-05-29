package com.hkg.dse.roaring;

import com.hkg.dse.common.DocId;

import java.util.Iterator;
import java.util.TreeMap;

/**
 * A simplified Roaring-style sparse bitset over 32-bit non-negative ints.
 *
 * <p>The bitset is partitioned into 2^16-wide <em>chunks</em> indexed by
 * the high 16 bits of each value. Each chunk holds the low 16 bits of
 * its members. This module implements only the bitmap container type
 * (8 192 bytes per chunk) for simplicity; the production Roaring library
 * also has array containers and run-length-encoded containers.</p>
 *
 * <p>O(1) membership tests are the load-bearing property — they are what
 * makes the ACL pre-filter inside the HNSW greedy walk affordable. Each
 * visited node triggers a membership test; without O(1), the HNSW
 * traversal budget would be burned on permission checks.</p>
 */
public final class RoaringBitset {

    private static final int CHUNK_WIDTH = 1 << 16;
    private static final int CHUNK_MASK = CHUNK_WIDTH - 1;
    /** 65 536 bits per chunk = 1 024 longs of 64 bits. */
    private static final int LONGS_PER_CHUNK = CHUNK_WIDTH / 64;

    /** Chunk key (high 16 bits) → bitmap container (1 024 longs). */
    private final TreeMap<Integer, long[]> chunks = new TreeMap<>();

    private int cardinality = 0;

    public void add(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("RoaringBitset values must be non-negative");
        }
        int chunkKey = value >>> 16;
        int offset = value & CHUNK_MASK;
        long[] chunk = chunks.computeIfAbsent(chunkKey, k -> new long[LONGS_PER_CHUNK]);
        int wordIdx = offset >>> 6;
        long mask = 1L << (offset & 63);
        if ((chunk[wordIdx] & mask) == 0L) {
            chunk[wordIdx] |= mask;
            cardinality++;
        }
    }

    public void add(DocId docId) {
        add(docId.value());
    }

    public boolean contains(int value) {
        if (value < 0) {
            return false;
        }
        int chunkKey = value >>> 16;
        long[] chunk = chunks.get(chunkKey);
        if (chunk == null) {
            return false;
        }
        int offset = value & CHUNK_MASK;
        int wordIdx = offset >>> 6;
        long mask = 1L << (offset & 63);
        return (chunk[wordIdx] & mask) != 0L;
    }

    public boolean contains(DocId docId) {
        return contains(docId.value());
    }

    public int cardinality() {
        return cardinality;
    }

    public boolean isEmpty() {
        return cardinality == 0;
    }

    /** Bitwise OR with {@code other}, mutating this. Used during ACL token union. */
    public void or(RoaringBitset other) {
        for (var entry : other.chunks.entrySet()) {
            long[] target = chunks.computeIfAbsent(entry.getKey(), k -> new long[LONGS_PER_CHUNK]);
            long[] source = entry.getValue();
            for (int i = 0; i < LONGS_PER_CHUNK; i++) {
                long oldWord = target[i];
                long newWord = oldWord | source[i];
                if (newWord != oldWord) {
                    cardinality += Long.bitCount(newWord) - Long.bitCount(oldWord);
                    target[i] = newWord;
                }
            }
        }
    }

    /**
     * Bitwise AND with {@code other}, mutating this. Used during boolean
     * AND query evaluation and tombstone filtering.
     */
    public void and(RoaringBitset other) {
        var iter = chunks.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            long[] target = entry.getValue();
            long[] source = other.chunks.get(entry.getKey());
            if (source == null) {
                iter.remove();
                continue;
            }
            int chunkCard = 0;
            for (int i = 0; i < LONGS_PER_CHUNK; i++) {
                target[i] &= source[i];
                chunkCard += Long.bitCount(target[i]);
            }
            if (chunkCard == 0) {
                iter.remove();
            }
        }
        recomputeCardinality();
    }

    /**
     * Bitwise AND-NOT with {@code other}, mutating this. Used to apply
     * tombstones to a candidate set (remove deleted doc-IDs).
     */
    public void andNot(RoaringBitset other) {
        for (var entry : other.chunks.entrySet()) {
            long[] target = chunks.get(entry.getKey());
            if (target == null) continue;
            long[] source = entry.getValue();
            for (int i = 0; i < LONGS_PER_CHUNK; i++) {
                target[i] &= ~source[i];
            }
        }
        // Remove now-empty chunks
        chunks.entrySet().removeIf(e -> {
            for (long w : e.getValue()) {
                if (w != 0L) return false;
            }
            return true;
        });
        recomputeCardinality();
    }

    /** Iterator over set values in ascending order. */
    public Iterator<Integer> iterator() {
        return new Iterator<>() {
            private final Iterator<java.util.Map.Entry<Integer, long[]>> chunkIter = chunks.entrySet().iterator();
            private Integer chunkKey = null;
            private long[] chunk = null;
            private int wordIdx = 0;
            private long currentWord = 0;
            private Integer next = computeNext();

            private Integer computeNext() {
                while (true) {
                    while (currentWord != 0L) {
                        int bit = Long.numberOfTrailingZeros(currentWord);
                        currentWord &= currentWord - 1L; // clear lowest set bit
                        return (chunkKey << 16) | (wordIdx * 64) | bit;
                    }
                    wordIdx++;
                    if (chunk == null || wordIdx >= LONGS_PER_CHUNK) {
                        if (!chunkIter.hasNext()) return null;
                        var entry = chunkIter.next();
                        chunkKey = entry.getKey();
                        chunk = entry.getValue();
                        wordIdx = 0;
                    }
                    currentWord = chunk[wordIdx];
                }
            }

            @Override
            public boolean hasNext() {
                return next != null;
            }

            @Override
            public Integer next() {
                if (next == null) throw new java.util.NoSuchElementException();
                int v = next;
                next = computeNext();
                return v;
            }
        };
    }

    /** Recompute cardinality from scratch (used after AND for correctness). */
    public void recomputeCardinality() {
        int total = 0;
        for (long[] chunk : chunks.values()) {
            for (long w : chunk) total += Long.bitCount(w);
        }
        this.cardinality = total;
    }
}
