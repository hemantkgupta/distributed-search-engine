package com.hkg.dse.broker;

import com.hkg.dse.common.Score;

import java.util.List;

/**
 * The broker's final response: a top-K and a partial-results indicator.
 * The {@code partial} flag is set to true whenever any shard missed the
 * per-shard timeout or returned a partial result of its own.
 */
public record BrokerResult(List<Score> topK, boolean partial) {

    public BrokerResult {
        topK = List.copyOf(topK);
    }
}
