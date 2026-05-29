package com.hkg.dse.pfor;

import java.util.List;

/**
 * A DAAT-style cursor over a {@link PostingList}, with per-block skip
 * for Block-Max WAND.
 *
 * <p>Operations:
 * <ul>
 *   <li>{@link #docId()} — current doc-ID, or {@link #NO_MORE_DOCS} after exhaustion</li>
 *   <li>{@link #impact()} — BM25 contribution at current position</li>
 *   <li>{@link #advance(int)} — advance cursor to first doc-ID ≥ target</li>
 *   <li>{@link #blockMaxImpact()} — per-block max impact at current position
 *       (for BMW per-block sum)</li>
 *   <li>{@link #blockMaxDocId()} — max doc-ID in current block (for skipping)</li>
 * </ul>
 *
 * <p>This is the data-flow primitive that lets the lexical query loop
 * intersect multiple posting lists efficiently while pruning blocks
 * that cannot beat the current top-K heap floor.</p>
 */
public final class PostingCursor {

    public static final int NO_MORE_DOCS = Integer.MAX_VALUE;

    private final List<PostingBlock> blocks;
    private int blockIdx;
    private int docIdx;
    private int currentDocId;
    private float currentImpact;

    public PostingCursor(PostingList list) {
        this.blocks = list.blocks();
        this.blockIdx = 0;
        this.docIdx = -1;
        this.currentDocId = -1;
        this.currentImpact = 0f;
        next();
    }

    public int docId() {
        return currentDocId;
    }

    public float impact() {
        return currentImpact;
    }

    public float blockMaxImpact() {
        if (blockIdx >= blocks.size()) return 0f;
        return blocks.get(blockIdx).maxImpact();
    }

    public int blockMaxDocId() {
        if (blockIdx >= blocks.size()) return NO_MORE_DOCS;
        return blocks.get(blockIdx).maxDocId();
    }

    /** Advance to next doc-ID (or NO_MORE_DOCS). */
    public int next() {
        if (blockIdx >= blocks.size()) {
            currentDocId = NO_MORE_DOCS;
            currentImpact = 0f;
            return currentDocId;
        }
        PostingBlock block = blocks.get(blockIdx);
        docIdx++;
        if (docIdx >= block.size()) {
            blockIdx++;
            docIdx = 0;
            if (blockIdx >= blocks.size()) {
                currentDocId = NO_MORE_DOCS;
                currentImpact = 0f;
                return currentDocId;
            }
            block = blocks.get(blockIdx);
        }
        currentDocId = block.docIds()[docIdx];
        currentImpact = block.tfImpacts()[docIdx];
        return currentDocId;
    }

    /**
     * Advance the cursor to the first doc-ID ≥ {@code target}. Uses
     * per-block max-doc-ID to skip whole blocks before scanning entries.
     */
    public int advance(int target) {
        while (blockIdx < blocks.size() && blocks.get(blockIdx).maxDocId() < target) {
            blockIdx++;
            docIdx = -1;
        }
        if (blockIdx >= blocks.size()) {
            currentDocId = NO_MORE_DOCS;
            currentImpact = 0f;
            return currentDocId;
        }
        // Scan within the current block (linear; tight loop)
        PostingBlock block = blocks.get(blockIdx);
        int[] ids = block.docIds();
        int i = Math.max(docIdx + 1, 0);
        while (i < ids.length && ids[i] < target) i++;
        if (i >= ids.length) {
            blockIdx++;
            docIdx = -1;
            return advance(target);
        }
        docIdx = i;
        currentDocId = ids[i];
        currentImpact = block.tfImpacts()[i];
        return currentDocId;
    }

    /** Skip past the end of the current block (used by BMW after a skip decision). */
    public int advancePastBlock() {
        if (blockIdx >= blocks.size()) {
            currentDocId = NO_MORE_DOCS;
            return currentDocId;
        }
        int target = blocks.get(blockIdx).maxDocId() + 1;
        blockIdx++;
        docIdx = -1;
        return advance(target);
    }

    public boolean exhausted() {
        return currentDocId == NO_MORE_DOCS;
    }
}
