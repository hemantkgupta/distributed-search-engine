package com.hkg.dse.shard;

import com.hkg.dse.common.Score;

import java.util.List;

/**
 * Result of one shard's per-query work: the top-K candidates plus an
 * {@code is_partial} flag indicating whether the shard had to abandon
 * work due to a timeout.
 *
 * <p>The broker uses {@code is_partial} to decide whether to surface a
 * partial-results indicator to the client. At 10 000-shard fan-out
 * scale, partial results are the norm rather than the exception.</p>
 */
public record ShardResult(List<Score> topK, boolean partial) {

    public static ShardResult complete(List<Score> topK) {
        return new ShardResult(List.copyOf(topK), false);
    }

    public static ShardResult partial(List<Score> topK) {
        return new ShardResult(List.copyOf(topK), true);
    }
}
