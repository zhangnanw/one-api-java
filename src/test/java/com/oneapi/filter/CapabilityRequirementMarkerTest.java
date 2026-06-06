package com.oneapi.filter;

import com.oneapi.model.MatchRule;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityRequirementMarkerTest {

    CapabilityRequirementMarker filter = new CapabilityRequirementMarker();

    @Test
    void capabilityMatch_setsRequired() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setMatchRule(new MatchRule.CapabilityMatch("reasoning"));

        filter.apply(ctx);

        assertThat(ctx.capabilityRequired()).isEqualTo("reasoning");
    }

    @Test
    void nonCapabilityMatch_doesNotSet() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setMatchRule(new MatchRule.AllMatch());

        filter.apply(ctx);

        assertThat(ctx.capabilityRequired()).isNull();
    }

    @Test
    void nullMatchRule_doesNotSet() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        // matchRule is null by default

        filter.apply(ctx);

        assertThat(ctx.capabilityRequired()).isNull();
    }
}
