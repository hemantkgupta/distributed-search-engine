package com.hkg.dse.pfor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A posting list as an ordered sequence of {@link PostingBlock}s.
 *
 * <p>Built by the indexer in append order during ingest; read by the
 * query loop via a {@link PostingCursor}. The builder pre-computes each
 * block's per-doc BM25 impact and the per-block max-impact for Block-Max
 * WAND pruning.</p>
 *
 * <p>The per-term BM25 contribution requires term IDF, doc length, and
 * the corpus average doc length — the builder receives a {@link Bm25Stats}
 * for these.</p>
 */
public final class PostingList {

    private final List<PostingBlock> blocks;
    private final int docFrequency;

    private PostingList(List<PostingBlock> blocks, int docFrequency) {
        this.blocks = List.copyOf(blocks);
        this.docFrequency = docFrequency;
    }

    public List<PostingBlock> blocks() {
        return blocks;
    }

    public int docFrequency() {
        return docFrequency;
    }

    public int totalSize() {
        return blocks.stream().mapToInt(PostingBlock::size).sum();
    }

    /**
     * Builder consuming (doc-ID, term-frequency) pairs in ascending doc-ID order.
     */
    public static final class Builder {

        private final List<PostingBlock> blocks = new ArrayList<>();
        private final int[] pendingDocIds = new int[PostingBlock.BLOCK_SIZE];
        private final float[] pendingImpacts = new float[PostingBlock.BLOCK_SIZE];
        private int pendingCount = 0;
        private int totalDocs = 0;
        private int lastDocId = -1;
        private final Bm25Stats stats;
        private final double idf;

        public Builder(Bm25Stats stats, int totalDocsInCorpus, int docFrequency) {
            this.stats = stats;
            this.idf = stats.idf(totalDocsInCorpus, docFrequency);
        }

        public Builder add(int docId, int termFrequency, int docLength) {
            if (docId <= lastDocId) {
                throw new IllegalArgumentException(
                    "doc-IDs must be strictly ascending: " + docId + " <= " + lastDocId);
            }
            lastDocId = docId;
            pendingDocIds[pendingCount] = docId;
            pendingImpacts[pendingCount] = (float) stats.bm25(idf, termFrequency, docLength);
            pendingCount++;
            totalDocs++;
            if (pendingCount == PostingBlock.BLOCK_SIZE) {
                flushBlock();
            }
            return this;
        }

        private void flushBlock() {
            int[] ids = Arrays.copyOf(pendingDocIds, pendingCount);
            float[] impacts = Arrays.copyOf(pendingImpacts, pendingCount);
            float maxImpact = 0f;
            for (float v : impacts) if (v > maxImpact) maxImpact = v;
            int maxDocId = ids[ids.length - 1];
            int firstDocId = ids[0];
            int gap = maxDocId - firstDocId;
            int bitWidth = gap == 0 ? 1 : 32 - Integer.numberOfLeadingZeros(gap);
            blocks.add(new PostingBlock(ids, impacts, maxDocId, maxImpact, bitWidth));
            pendingCount = 0;
        }

        public PostingList build() {
            if (pendingCount > 0) flushBlock();
            return new PostingList(blocks, totalDocs);
        }
    }
}
