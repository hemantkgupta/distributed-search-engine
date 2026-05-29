package com.hkg.dse.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricsRegistryTest {

    @Test
    void counterStartsAtZero() {
        MetricsRegistry r = new MetricsRegistry();
        assertThat(r.counter("missing")).isZero();
    }

    @Test
    void incrementsAccumulate() {
        MetricsRegistry r = new MetricsRegistry();
        r.incrementCounter("queries");
        r.incrementCounter("queries");
        r.incrementCounter("queries", 5);
        assertThat(r.counter("queries")).isEqualTo(7);
    }

    @Test
    void gaugeSetReplacesValue() {
        MetricsRegistry r = new MetricsRegistry();
        r.setGauge("active_shards", 1000);
        r.setGauge("active_shards", 1200);
        assertThat(r.gauge("active_shards")).isEqualTo(1200);
    }

    @Test
    void histogramPercentilesReflectSamples() {
        MetricsRegistry r = new MetricsRegistry();
        for (int i = 1; i <= 100; i++) {
            r.observe("query_ms", i);
        }
        assertThat(r.histogram("query_ms").count()).isEqualTo(100);
        assertThat(r.histogram("query_ms").percentile(0.5)).isBetween(49.0, 51.0);
        assertThat(r.histogram("query_ms").percentile(0.99)).isBetween(98.0, 100.0);
    }

    @Test
    void emptyHistogramReturnsZero() {
        MetricsRegistry r = new MetricsRegistry();
        assertThat(r.histogram("nope").percentile(0.5)).isZero();
    }

    @Test
    void invalidPercentileRejected() {
        MetricsRegistry r = new MetricsRegistry();
        r.observe("x", 1.0);
        assertThatThrownBy(() -> r.histogram("x").percentile(1.5))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
