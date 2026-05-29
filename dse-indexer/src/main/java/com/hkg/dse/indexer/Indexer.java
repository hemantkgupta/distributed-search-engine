package com.hkg.dse.indexer;

import com.hkg.dse.common.Term;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.segment.SegmentBuilder;
import com.hkg.dse.segment.UnifiedSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Accepts {@link DocumentEvent}s, accumulates them into an in-memory
 * segment, and flushes when the doc-count threshold fires.
 *
 * <p>The flush threshold stands in for the production "64 MB or 10 min"
 * trigger — here it is a configurable doc-count threshold for testing.</p>
 *
 * <p>Sealing is one-way: a sealed indexer cannot accept more docs. To
 * keep ingesting, the caller starts a new {@code Indexer}.</p>
 */
public final class Indexer {

    private final List<DocumentEvent> pending = new ArrayList<>();
    private final int flushAtDocCount;
    private final ProductQuantizer pq;
    private final int hnswMaxNeighbours;
    private boolean sealed = false;

    public Indexer(ProductQuantizer pq, int hnswMaxNeighbours, int flushAtDocCount) {
        if (flushAtDocCount < 1) {
            throw new IllegalArgumentException("flushAtDocCount must be >= 1");
        }
        this.pq = pq;
        this.hnswMaxNeighbours = hnswMaxNeighbours;
        this.flushAtDocCount = flushAtDocCount;
    }

    /** Returns {@code true} if the indexer should flush now. */
    public boolean accept(DocumentEvent event) {
        if (sealed) throw new IllegalStateException("indexer is sealed");
        pending.add(event);
        return pending.size() >= flushAtDocCount;
    }

    public int pendingCount() {
        return pending.size();
    }

    /**
     * Build a sealed segment containing all pending events; the indexer
     * cannot accept more docs after flush. The caller starts a new
     * {@code Indexer} to continue ingest.
     */
    public UnifiedSegment flush() {
        sealed = true;
        SegmentBuilder builder = new SegmentBuilder(pq, hnswMaxNeighbours);
        for (DocumentEvent event : pending) {
            List<Term> allTerms = new ArrayList<>(event.contentTerms());
            for (String aclToken : event.aclTokens()) {
                allTerms.add(Term.aclRead(aclToken));
            }
            builder.addDocument(
                event.docId(),
                allTerms,
                event.embedding(),
                event.payload(),
                event.colbertTokens()
            );
        }
        return builder.build();
    }

    public boolean isSealed() {
        return sealed;
    }
}
