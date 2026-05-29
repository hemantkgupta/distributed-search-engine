package com.hkg.dse.segment;

import com.hkg.dse.colbert.ColbertReranker;
import com.hkg.dse.common.DocId;
import com.hkg.dse.hnsw.HnswGraph;
import com.hkg.dse.inverted.InvertedIndex;
import com.hkg.dse.roaring.RoaringBitset;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A unified segment containing all retrieval modalities for its share
 * of the doc-ID space.
 *
 * <p>Doc-IDs are dense within a segment and form the join key across:
 * <ul>
 *   <li>{@link InvertedIndex}: lexical retrieval (term → posting list)</li>
 *   <li>{@link HnswGraph}: vector retrieval (PQ-compressed embeddings)</li>
 *   <li>Forward store: rendering payloads (Map&lt;docId, payload&gt;)</li>
 *   <li>ColBERT token store: per-token tensors for rerank</li>
 *   <li>{@link RoaringBitset} tombstone bitmap: deleted doc-IDs</li>
 * </ul>
 *
 * <p>The segment is immutable after construction. Per-doc updates create
 * a new doc-ID in a new segment and tombstone the old. Tiered merges
 * consolidate segments and rebuild the HNSW graph over surviving doc-IDs
 * to eliminate unreachable points — see {@code dse-merge}.</p>
 */
public final class UnifiedSegment {

    private final UUID id;
    private final InvertedIndex invertedIndex;
    private final HnswGraph hnswGraph;
    private final ColbertReranker colbert;
    private final Map<Integer, String> forwardStore;
    private final RoaringBitset tombstones;
    private final int liveDocCount;

    public UnifiedSegment(
        UUID id,
        InvertedIndex invertedIndex,
        HnswGraph hnswGraph,
        ColbertReranker colbert,
        Map<Integer, String> forwardStore,
        RoaringBitset tombstones
    ) {
        this.id = id;
        this.invertedIndex = invertedIndex;
        this.hnswGraph = hnswGraph;
        this.colbert = colbert;
        this.forwardStore = Map.copyOf(forwardStore);
        this.tombstones = tombstones;
        this.liveDocCount = forwardStore.size() - tombstones.cardinality();
    }

    public UUID id() { return id; }
    public InvertedIndex invertedIndex() { return invertedIndex; }
    public HnswGraph hnswGraph() { return hnswGraph; }
    public ColbertReranker colbert() { return colbert; }
    public Map<Integer, String> forwardStore() { return forwardStore; }
    public RoaringBitset tombstones() { return tombstones; }
    public int liveDocCount() { return liveDocCount; }

    public boolean isLive(DocId docId) {
        return forwardStore.containsKey(docId.value()) && !tombstones.contains(docId);
    }

    /** Mark a doc-ID as tombstoned (in place — the only mutation allowed). */
    public void markTombstone(DocId docId) {
        tombstones.add(docId);
    }

    public double tombstoneRatio() {
        int total = forwardStore.size();
        if (total == 0) return 0.0;
        return (double) tombstones.cardinality() / total;
    }
}
