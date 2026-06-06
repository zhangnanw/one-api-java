package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveStatusFilterTest {

    ActiveStatusFilter filter = new ActiveStatusFilter();

    @Test
    void rawStatus_kept() {
        RelayContext ctx = ctx(rv(1, InstanceRepo.STATUS_RAW));
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
    }

    @Test
    void taggedStatus_kept() {
        RelayContext ctx = ctx(rv(1, InstanceRepo.STATUS_TAGGED));
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(1);
    }

    @Test
    void disabledStatus_excluded() {
        RelayContext ctx = ctx(rv(1, InstanceRepo.STATUS_DISABLED));
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void deprecatedStatus_excluded() {
        RelayContext ctx = ctx(rv(1, InstanceRepo.STATUS_DEPRECATED));
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void mixed_onlyActiveKept() {
        RelayContext ctx = ctx(
            rv(1, InstanceRepo.STATUS_RAW),
            rv(2, InstanceRepo.STATUS_DISABLED),
            rv(3, InstanceRepo.STATUS_TAGGED),
            rv(4, InstanceRepo.STATUS_DEPRECATED)
        );
        filter.apply(ctx);
        assertThat(ctx.candidates()).hasSize(2);
        assertThat(ctx.candidates()).extracting(RoutedVendor::instanceId)
            .containsExactly(1, 3);
    }

    @Test
    void emptyCandidates_returnsEmpty() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setCandidates(List.of());
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    private static RoutedVendor rv(int id, int status) {
        return new RoutedVendor(null, "model", "upstream", id, "", "{}", status);
    }

    private static RelayContext ctx(RoutedVendor... candidates) {
        RelayContext c = new RelayContext("kimi-k2.6");
        c.setCandidates(List.of(candidates));
        return c;
    }
}
