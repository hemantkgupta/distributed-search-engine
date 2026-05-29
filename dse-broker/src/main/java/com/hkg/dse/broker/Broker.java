package com.hkg.dse.broker;

import com.hkg.dse.common.Score;
import com.hkg.dse.rrf.ReciprocalRankFusion;
import com.hkg.dse.shard.ContentNode;
import com.hkg.dse.shard.Query;
import com.hkg.dse.shard.ShardResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Scatter-gather broker. Fans the query to all shards in parallel,
 * waits up to {@code perShardTimeout} per shard, RRF-merges the
 * returned candidates, and emits partial-result flag if any shard
 * missed the deadline.
 */
public final class Broker {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(80);

    private final List<ContentNode> shards;
    private final Duration perShardTimeout;
    private final ReciprocalRankFusion rrf;
    private final ExecutorService executor;

    public Broker(List<ContentNode> shards) {
        this(shards, DEFAULT_TIMEOUT, new ReciprocalRankFusion());
    }

    public Broker(List<ContentNode> shards, Duration perShardTimeout, ReciprocalRankFusion rrf) {
        this.shards = List.copyOf(shards);
        this.perShardTimeout = perShardTimeout;
        this.rrf = rrf;
        this.executor = Executors.newCachedThreadPool();
    }

    public BrokerResult search(Query query) {
        List<Future<ShardResult>> futures = new ArrayList<>();
        for (ContentNode shard : shards) {
            futures.add(executor.submit(() -> shard.execute(query)));
        }
        boolean partial = false;
        List<List<Score>> perShardTopK = new ArrayList<>();
        long deadlineMillis = System.currentTimeMillis() + perShardTimeout.toMillis();
        for (Future<ShardResult> f : futures) {
            try {
                long remaining = deadlineMillis - System.currentTimeMillis();
                if (remaining < 0) remaining = 0;
                ShardResult shardResult = f.get(remaining, TimeUnit.MILLISECONDS);
                perShardTopK.add(shardResult.topK());
                if (shardResult.partial()) partial = true;
            } catch (TimeoutException te) {
                partial = true;
                f.cancel(true);
            } catch (Exception e) {
                partial = true;
            }
        }

        @SuppressWarnings("unchecked")
        List<Score>[] channels = perShardTopK.toArray(new List[0]);
        List<Score> fused = rrf.fuse(query.topK(), channels);
        return new BrokerResult(fused, partial);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
