package com.hkg.dse.pfor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Bm25StatsTest {

    @Test
    void defaultsAreLuceneStandard() {
        Bm25Stats s = Bm25Stats.withDefaults(100.0);
        assertThat(s.k1()).isEqualTo(1.2);
        assertThat(s.b()).isEqualTo(0.75);
    }

    @Test
    void idfHigherForRareTerms() {
        Bm25Stats s = Bm25Stats.withDefaults(100.0);
        double rare = s.idf(1_000_000, 100);
        double common = s.idf(1_000_000, 100_000);
        assertThat(rare).isGreaterThan(common);
    }

    @Test
    void bm25IncreasesWithTfButSaturates() {
        Bm25Stats s = Bm25Stats.withDefaults(100.0);
        double idf = s.idf(1_000_000, 1_000);
        double tf1 = s.bm25(idf, 1, 100);
        double tf2 = s.bm25(idf, 2, 100);
        double tf10 = s.bm25(idf, 10, 100);
        double tf100 = s.bm25(idf, 100, 100);
        assertThat(tf2).isGreaterThan(tf1);
        assertThat(tf10).isGreaterThan(tf2);
        // saturation: tf=100 should not be 100× tf=1
        assertThat(tf100 / tf1).isLessThan(50.0);
    }

    @Test
    void longerDocIsPenalised() {
        Bm25Stats s = Bm25Stats.withDefaults(100.0);
        double idf = s.idf(1_000_000, 1_000);
        double shortDoc = s.bm25(idf, 5, 50);
        double longDoc = s.bm25(idf, 5, 500);
        assertThat(shortDoc).isGreaterThan(longDoc);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new Bm25Stats(-1, 0.75, 100.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bm25Stats(1.2, 1.5, 100.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Bm25Stats(1.2, 0.75, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
