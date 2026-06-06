package com.oneapi.filter;

import com.oneapi.model.MetaKeys;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityInstanceFilterTest {

    CapabilityInstanceFilter filter = new CapabilityInstanceFilter();

    @Test
    void reasoningCapability_match() {
        RelayContext ctx = ctx("reasoning",
            rv(1, MetaKeys.CAPABILITY_REASONING),
            rv(2, MetaKeys.CAPABILITY_VISION)
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
        assertThat(ctx.candidates().get(0).instanceId()).isEqualTo(1);
    }

    @Test
    void noCapabilityRequired_passesThrough() {
        RelayContext ctx = ctx(null,
            rv(1, MetaKeys.CAPABILITY_REASONING),
            rv(2, MetaKeys.CAPABILITY_VISION)
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(2);
    }

    @Test
    void emptyCandidates_returnsEmpty() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setCapabilityRequired("reasoning");
        ctx.setCandidates(List.of());
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void nullCandidates_passesThrough() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setCapabilityRequired("reasoning");
        // candidates null — filter should not NPE
        filter.apply(ctx);
        assertThat(ctx.candidates()).isNull();
    }

    // --- helpers ---

    private static RoutedVendor rv(int instanceId, String tag) {
        String meta = "{\"tags\":[\"" + tag + "\"]}";
        return new RoutedVendor(null, "model", "upstream", instanceId, "", meta, 1);
    }

    private static RelayContext ctx(String capability, RoutedVendor... candidates) {
        RelayContext c = new RelayContext("kimi-k2.6");
        c.setCapabilityRequired(capability);
        c.setCandidates(List.of(candidates));
        return c;
    }
}
