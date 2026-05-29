package com.hkg.dse.node;

import com.hkg.dse.common.DocId;

/**
 * Document-partitioned routing: each doc-ID hashes to exactly one shard.
 *
 * <p>This is the load-bearing sharding decision — see
 * {@code tradeoffs/document-vs-term-partitioned-sharding.md}. With
 * document-partitioning, a doc's terms + vector + ACL + ColBERT tokens
 * all live on the same shard, queries fan out (or hit Tier-1 only),
 * and per-doc updates are atomic on a single shard.</p>
 *
 * <p>Term-partitioning would require shipping gigabytes of posting
 * lists across the cluster for multi-term queries — empirically
 * infeasible above ~1 M QPS. We don't implement it.</p>
 */
public final class DocumentRouter {

    private final int shardCount;

    public DocumentRouter(int shardCount) {
        if (shardCount < 1) throw new IllegalArgumentException("shardCount must be >= 1");
        this.shardCount = shardCount;
    }

    public int shardCount() {
        return shardCount;
    }

    public int shardFor(DocId docId) {
        // Mixed integer hash — avoids skew from sequential doc-IDs sharing
        // shards by hash modulo cluster size.
        int h = docId.value();
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        int s = h % shardCount;
        return s < 0 ? s + shardCount : s;
    }
}
