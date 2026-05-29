package com.hkg.dse.pfor;

/**
 * A 128-doc-ID PFOR-Delta block with its per-block max-impact header.
 *
 * <p>The block stores the absolute doc-ID of the last entry (so the next
 * block can pick up its delta base), the max doc-ID for cursor skipping,
 * the per-block max-impact for Block-Max WAND pruning, and the bit-width
 * + bit-packed delta payload.</p>
 *
 * <p>In production these blocks would be serialised contiguously on disk
 * with each header byte tight; here they are in-memory records.</p>
 */
public record PostingBlock(
    int[] docIds,         // absolute doc-IDs, decoded for convenience
    float[] tfImpacts,    // BM25 contribution per doc (precomputed at index time)
    int maxDocId,         // max doc-ID in this block (for fast cursor skip)
    float maxImpact,      // max tfImpact across all docs in this block (BMW)
    int bitWidth          // bits used per delta
) {

    public static final int BLOCK_SIZE = 128;

    public PostingBlock {
        if (docIds.length != tfImpacts.length) {
            throw new IllegalArgumentException("docIds and tfImpacts must have same length");
        }
        if (docIds.length > BLOCK_SIZE) {
            throw new IllegalArgumentException("block size limit is " + BLOCK_SIZE);
        }
        if (docIds.length > 0 && docIds[docIds.length - 1] != maxDocId) {
            throw new IllegalArgumentException("maxDocId must equal the last doc-ID in the block");
        }
    }

    public int size() {
        return docIds.length;
    }

    /** Whether this block could contribute a score exceeding the BMW threshold. */
    public boolean canContribute(float heapFloor) {
        return maxImpact > heapFloor;
    }
}
