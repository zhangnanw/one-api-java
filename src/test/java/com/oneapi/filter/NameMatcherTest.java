package com.oneapi.filter;

import com.oneapi.service.InstanceService;
import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.util.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NameMatcherTest {

    @Mock
    InstanceService instanceService;

    private static final String PHYSICAL_MODEL = "doubao-seed-2.0-pro";

    @Test
    void physicalModelName_markedAsMatchedPhysical_withError() {
        when(instanceService.existsByModelName(PHYSICAL_MODEL)).thenReturn(true);

        RelayContext ctx = TestFixtures.relayContext(null, null, PHYSICAL_MODEL);
        new NameMatcher(instanceService).apply(ctx);

        assertThat(ctx.matchedPhysical()).isTrue();
        assertThat(ctx.hasError()).isTrue();
        assertThat(ctx.error()).isInstanceOf(RelayError.DirectUseForbidden.class);
        assertThat(ctx.errorMessage()).contains("physical model");
        assertThat(ctx.errorMessage()).contains("virtual model");
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.routingModelName()).isNull();
    }

    @Test
    void unknownModel_skipsMatchedPhysical() {
        when(instanceService.existsByModelName(PHYSICAL_MODEL)).thenReturn(false);

        RelayContext ctx = TestFixtures.relayContext(null, null, PHYSICAL_MODEL);
        new NameMatcher(instanceService).apply(ctx);

        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.routingModelName()).isNull();
        assertThat(ctx.matchedPhysical()).isFalse();
        assertThat(ctx.hasError()).isFalse();
    }

    @Test
    void emptyModel_earlyReturn() {
        RelayContext ctx = TestFixtures.relayContext(null, null, "");
        new NameMatcher(instanceService).apply(ctx);

        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.routingModelName()).isNull();
        assertThat(ctx.matchedPhysical()).isFalse();
        assertThat(ctx.hasError()).isFalse();
    }

    @Test
    void requireVirtualModel_disabled_skipsPhysicalCheck() {
        var r = new RelayContext(PHYSICAL_MODEL);
        new NameMatcher(instanceService, false).apply(r);

        assertThat(r.matchedPhysical()).isFalse();
        assertThat(r.hasError()).isFalse();
    }
}
