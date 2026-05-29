package com.hkg.dse.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocIdTest {

    @Test
    void rejectsNegativeIds() {
        assertThatThrownBy(() -> DocId.of(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroIsValid() {
        assertThat(DocId.of(0).value()).isEqualTo(0);
    }

    @Test
    void naturalOrderIsAscending() {
        DocId a = DocId.of(3);
        DocId b = DocId.of(7);
        assertThat(a.compareTo(b)).isNegative();
        assertThat(b.compareTo(a)).isPositive();
    }
}
