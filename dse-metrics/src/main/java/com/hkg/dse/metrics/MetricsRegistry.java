package com.hkg.dse.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal in-process metrics registry — counters, gauges, and a
 * percentile histogram. A stand-in for Prometheus / OpenTelemetry
 * exporters.
 *
 * <p>Production search engines export the following families:
 * <ul>
 *   <li>{@code search_shard_*_ms} latency per stage</li>
 *   <li>{@code search_qps_*} per-tier / per-tenant QPS</li>
 *   <li>{@code merge_io_bytes_per_sec}, {@code tombstone_ratio_pct}</li>
 *   <li>{@code recall_at_1000_first_stage}, {@code ndcg_at_10_offline}</li>
 *   <li>{@code embedding_drift_score}, {@code pq_codebook_age_days}</li>
 * </ul>
 */
public final class MetricsRegistry {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final Map<String, Histogram> histograms = new ConcurrentHashMap<>();

    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }

    public void incrementCounter(String name, long delta) {
        counters.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
    }

    public long counter(String name) {
        AtomicLong c = counters.get(name);
        return c == null ? 0 : c.get();
    }

    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    public long gauge(String name) {
        AtomicLong g = gauges.get(name);
        return g == null ? 0 : g.get();
    }

    public void observe(String histName, double value) {
        histograms.computeIfAbsent(histName, k -> new Histogram()).observe(value);
    }

    public Histogram histogram(String name) {
        return histograms.computeIfAbsent(name, k -> new Histogram());
    }

    public static final class Histogram {
        private final List<Double> samples = new ArrayList<>();

        public synchronized void observe(double value) {
            samples.add(value);
        }

        public synchronized double percentile(double p) {
            if (samples.isEmpty()) return 0;
            if (p < 0 || p > 1) {
                throw new IllegalArgumentException("p must be in [0, 1]");
            }
            List<Double> sorted = new ArrayList<>(samples);
            sorted.sort(Comparator.naturalOrder());
            int idx = (int) Math.floor(p * (sorted.size() - 1));
            return sorted.get(idx);
        }

        public synchronized long count() {
            return samples.size();
        }
    }
}
