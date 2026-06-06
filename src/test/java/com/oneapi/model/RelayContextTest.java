package com.oneapi.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelayContextTest {

    @Test
    void initialState_noError() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        assertThat(ctx.requestedModel()).isEqualTo("kimi-k2.6");
        assertThat(ctx.hasError()).isFalse();
        assertThat(ctx.candidates()).isNull();
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.matchedPhysical()).isFalse();
    }

    @Test
    void markError_setsErrorAndMessage() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        RelayError.ModelNotFound err = new RelayError.ModelNotFound("kimi-k2.6");
        ctx.markError(err, "model not found");

        assertThat(ctx.hasError()).isTrue();
        assertThat(ctx.error()).isEqualTo(err);
        assertThat(ctx.errorMessage()).isEqualTo("model not found");
    }

    @Test
    void settersAndGetters_roundTrip() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setUpstreamModel("deepseek-v3");
        ctx.setMatchRule(new MatchRule.AllMatch());
        ctx.setMatchedPhysical(true);
        ctx.setCapabilityRequired("reasoning");
        ctx.setReasoning(true);

        assertThat(ctx.upstreamModel()).isEqualTo("deepseek-v3");
        assertThat(ctx.matchRule()).isInstanceOf(MatchRule.AllMatch.class);
        assertThat(ctx.matchedPhysical()).isTrue();
        assertThat(ctx.capabilityRequired()).isEqualTo("reasoning");
        assertThat(ctx.reasoning()).isTrue();
    }
}
