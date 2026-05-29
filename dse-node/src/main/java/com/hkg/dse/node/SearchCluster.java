package com.hkg.dse.node;

import com.hkg.dse.broker.Broker;
import com.hkg.dse.broker.BrokerResult;
import com.hkg.dse.common.DocId;
import com.hkg.dse.hnsw.ProductQuantizer;
import com.hkg.dse.indexer.DocumentEvent;
import com.hkg.dse.indexer.Indexer;
import com.hkg.dse.segment.UnifiedSegment;
import com.hkg.dse.shard.ContentNode;
import com.hkg.dse.shard.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * End-to-end search cluster: a router, a per-shard {@link Indexer}, a
 * per-shard {@link ContentNode}, and one {@link Broker} that scatter-
 * gathers queries.
 *
 * <p>This is the harness that proves the architecture's invariants
 * compose: documents route to shards by hash, each shard's unified
 * segment owns lexical + vector + ColBERT + ACL terms + tombstones
 * for its slice of the doc-ID space, and the broker fuses across
 * shards via RRF.</p>
 */
public final class SearchCluster {

    private final DocumentRouter router;
    private final Map<Integer, Indexer> perShardIndexer = new HashMap<>();
    private final Map<Integer, ContentNode> perShardNode = new HashMap<>();
    private final ProductQuantizer pq;
    private final int hnswMaxNeighbours;
    private final int flushThreshold;
    private Broker broker;

    public SearchCluster(int shardCount, ProductQuantizer pq,
                         int hnswMaxNeighbours, int flushThreshold) {
        this.router = new DocumentRouter(shardCount);
        this.pq = pq;
        this.hnswMaxNeighbours = hnswMaxNeighbours;
        this.flushThreshold = flushThreshold;
        for (int i = 0; i < shardCount; i++) {
            perShardIndexer.put(i, new Indexer(pq, hnswMaxNeighbours, flushThreshold));
        }
    }

    public void ingest(DocumentEvent event) {
        int shard = router.shardFor(event.docId());
        Indexer indexer = perShardIndexer.get(shard);
        if (indexer.isSealed()) {
            // The previous batch flushed; start a new indexer for this shard
            indexer = new Indexer(pq, hnswMaxNeighbours, flushThreshold);
            perShardIndexer.put(shard, indexer);
        }
        boolean shouldFlush = indexer.accept(event);
        if (shouldFlush) flushShard(shard);
    }

    /** Force all shards to flush their pending docs into queryable segments. */
    public void commitAll() {
        for (int shard : new ArrayList<>(perShardIndexer.keySet())) {
            Indexer i = perShardIndexer.get(shard);
            if (i != null && !i.isSealed() && i.pendingCount() > 0) {
                flushShard(shard);
            }
        }
    }

    private void flushShard(int shard) {
        Indexer indexer = perShardIndexer.get(shard);
        UnifiedSegment seg = indexer.flush();
        perShardNode.put(shard, new ContentNode(seg));
        perShardIndexer.put(shard, new Indexer(pq, hnswMaxNeighbours, flushThreshold));
        rebuildBroker();
    }

    private void rebuildBroker() {
        if (broker != null) broker.shutdown();
        broker = new Broker(new ArrayList<>(perShardNode.values()));
    }

    public BrokerResult search(Query query) {
        if (broker == null) {
            broker = new Broker(List.of());
        }
        return broker.search(query);
    }

    /** Mark a doc-ID as tombstoned on the shard that owns it. */
    public void tombstone(DocId docId) {
        int shard = router.shardFor(docId);
        ContentNode node = perShardNode.get(shard);
        if (node == null) return;  // no segment yet; nothing to tombstone
        // The tombstone is applied to all segments on that shard
        node.segmentForTombstone().markTombstone(docId);
    }

    public int liveSegmentCount() {
        return perShardNode.size();
    }

    public void shutdown() {
        if (broker != null) broker.shutdown();
    }
}
