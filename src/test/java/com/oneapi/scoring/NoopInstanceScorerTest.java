package com.oneapi.scoring;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NoopInstanceScorerTest {

    @Test
    void score_returnsEmptyList() {
        var scorer = new NoopInstanceScorer();
        var result = scorer.score(List.of(), null);
        assertThat(result).isEmpty();
    }
}
