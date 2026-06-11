package com.oneapi.filter;

import com.oneapi.model.MatchRule;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LayerFilter}.
 *
 * Stage 3 — when matchRule is {@link MatchRule.LayerMatch}, filter candidates
 * whose instance meta layer matches the required layer.
 */
class LayerFilterTest {

    LayerFilter filter = new LayerFilter();

    @Test
    void noLayerRule_passesThrough() {
        RelayContext ctx = ctx(new MatchRule.AllMatch(), rv(1, "premium"), rv(2, "free"));
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(2);
    }

    @Test
    void matchingLayer_keeps() {
        RelayContext ctx = ctx(
            new MatchRule.LayerMatch("premium"),
            rv(1, "premium"),
            rv(2, "free")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
        assertThat(ctx.candidates().get(0).instanceId()).isEqualTo(1);
    }

    @Test
    void noMatch_excludes() {
        RelayContext ctx = ctx(
            new MatchRule.LayerMatch("premium"),
            rv(1, "free"),
            rv(2, "trial")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void emptyLayerRule_passesThrough() {
        RelayContext ctx = ctx(
            new MatchRule.LayerMatch(""),
            rv(1, "free")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
    }

    @Test
    void nullLayerRule_passesThrough() {
        RelayContext ctx = ctx(
            new MatchRule.LayerMatch(null),
            rv(1, "free")
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
    }

    @Test
    void emptyCandidates_returnsEmpty() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setMatchRule(new MatchRule.LayerMatch("premium"));
        ctx.setCandidates(List.of());
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    // --- helpers ---

    private static RoutedVendor rv(int instanceId, String layer) {
        String meta = layer.isEmpty() ? "{}" : "{\"layer\":\"" + layer + "\"}";
        return new RoutedVendor(
            null,
            "model-" + instanceId,
            "upstream-" + instanceId,
            instanceId,
            "",
            meta,
            1
        , 0f, "payg");
    }

    private static RelayContext ctx(MatchRule rule, RoutedVendor... candidates) {
        RelayContext c = new RelayContext("kimi-k2.6");
        c.setMatchRule(rule);
        c.setCandidates(List.of(candidates));
        return c;
    }
}
