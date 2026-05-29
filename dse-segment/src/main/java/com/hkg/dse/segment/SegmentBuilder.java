package com.hkg.dse.segment;

import com.hkg.dse.colbert.ColbertReranker;
import com.hkg.dse.common.DocId;
import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.HnswGraph;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.inverted.InvertedIndex;
import com.hkg.dse.roaring.RoaringBitset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builder that accepts documents (text + terms + vector + tokens) and
 * produces a {@link UnifiedSegment} consisting of an inverted index,
 * an HNSW graph, a forward store, a ColBERT token store, and an empty
 * tombstone bitmap.
 *
 * <p>This is the indexer-side abstraction: in production, the indexer
 * cluster ({@code dse-indexer}) drives this builder to consume a CDC
 * stream and flush a new segment every ~64 MB or 10 min.</p>
 */
public final class SegmentBuilder {

    private final InvertedIndex.Builder invertedBuilder = new InvertedIndex.Builder();
    private final Map<Integer, float[]> pendingVectors = new HashMap<>();
    private final Map<Integer, String> pendingForward = new HashMap<>();
    private final Map<Integer, float[][]> pendingTokens = new HashMap<>();
    private final ProductQuantizer pq;
    private final int hnswMaxNeighbours;

    public SegmentBuilder(ProductQuantizer pq, int hnswMaxNeighbours) {
        this.pq = pq;
        this.hnswMaxNeighbours = hnswMaxNeighbours;
    }

    public SegmentBuilder addDocument(
        DocId docId,
        List<Term> terms,
        float[] vector,
        String forwardPayload,
        float[][] colbertTokens
    ) {
        invertedBuilder.addDocument(docId.value(), terms);
        pendingVectors.put(docId.value(), vector);
        pendingForward.put(docId.value(), forwardPayload);
        pendingTokens.put(docId.value(), colbertTokens);
        return this;
    }

    public UnifiedSegment build() {
        InvertedIndex inverted = invertedBuilder.build();
        HnswGraph graph = new HnswGraph(pq, hnswMaxNeighbours);
        // Insert vectors in deterministic doc-ID order
        List<Integer> docIds = new ArrayList<>(pendingVectors.keySet());
        docIds.sort(Integer::compareTo);
        for (int id : docIds) {
            graph.insert(DocId.of(id), pendingVectors.get(id));
        }
        ColbertReranker colbert = new ColbertReranker(pendingTokens);
        return new UnifiedSegment(
            UUID.randomUUID(),
            inverted,
            graph,
            colbert,
            pendingForward,
            new RoaringBitset()
        );
    }
}
