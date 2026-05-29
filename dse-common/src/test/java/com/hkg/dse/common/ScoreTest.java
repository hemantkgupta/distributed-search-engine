package com.hkg.dse.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTest {

    @Test
    void higherScoreSortsFirst() {
        Score a = Score.of(1, 5.0);
        Score b = Score.of(2, 8.0);
        List<Score> list = new java.util.ArrayList<>(Arrays.asList(a, b));
        java.util.Collections.sort(list);
        assertThat(list).containsExactly(b, a);
    }

    @Test
    void docIdBreaksTie() {
        Score a = Score.of(7, 5.0);
        Score b = Score.of(2, 5.0);
        assertThat(a.compareTo(b)).isPositive(); // docId 7 > docId 2 → sorts after
    }
}
