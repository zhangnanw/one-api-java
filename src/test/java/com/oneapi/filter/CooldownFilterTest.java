package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.Vendor;
import com.oneapi.service.CooldownService;
import com.oneapi.service.RouterService.RoutedVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CooldownFilter}.
 *
 * Stage 3 — exclude candidates where instance or vendor is in cooldown.
 */
@ExtendWith(MockitoExtension.class)
class CooldownFilterTest {

    @Mock
    CooldownService cooldown;

    CooldownFilter filter;
    Vendor vendorA;
    Vendor vendorB;

    @BeforeEach
    void setUp() {
        filter = new CooldownFilter(cooldown);
        vendorA = vendor(1, "vendor-a");
        vendorB = vendor(2, "vendor-b");
    }

    @Test
    void neitherInCooldown_keepsBoth() {
        when(cooldown.isInstanceInCooldown(anyInt(), anyString())).thenReturn(false);
        when(cooldown.isVendorInCooldown(anyInt())).thenReturn(false);

        RelayContext ctx = ctx(rv(1, vendorA), rv(2, vendorB));
        filter.apply(ctx);

        assertThat(ctx.candidates()).hasSize(2);
    }

    @Test
    void instanceInCooldown_excluded() {
        when(cooldown.isInstanceInCooldown(1, "tag-a")).thenReturn(true);
        when(cooldown.isInstanceInCooldown(2, "tag-b")).thenReturn(false);
        when(cooldown.isVendorInCooldown(anyInt())).thenReturn(false);

        RelayContext ctx = ctx(rv(1, vendorA, "tag-a"), rv(2, vendorB, "tag-b"));
        filter.apply(ctx);

        assertThat(ctx.candidates()).hasSize(1);
        assertThat(ctx.candidates().get(0).instanceId()).isEqualTo(2);
    }

    @Test
    void vendorInCooldown_excluded() {
        when(cooldown.isInstanceInCooldown(anyInt(), anyString())).thenReturn(false);
        when(cooldown.isVendorInCooldown(1)).thenReturn(true);
        when(cooldown.isVendorInCooldown(2)).thenReturn(false);

        RelayContext ctx = ctx(rv(1, vendorA), rv(2, vendorB));
        filter.apply(ctx);

        assertThat(ctx.candidates()).hasSize(1);
        assertThat(ctx.candidates().get(0).vendor().getId()).isEqualTo(2);
    }

    @Test
    void vendorNull_skipped() {
        when(cooldown.isInstanceInCooldown(anyInt(), anyString())).thenReturn(false);
        // isVendorInCooldown should NOT be called for null vendor

        RelayContext ctx = ctx(rv(1, null));
        filter.apply(ctx);

        assertThat(ctx.candidates()).hasSize(1);
    }

    @Test
    void emptyCandidates_returnsEmpty() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        ctx.setCandidates(List.of());
        filter.apply(ctx);
        assertThat(ctx.candidates()).isEmpty();
    }

    @Test
    void nullCandidates_passesThrough() {
        RelayContext ctx = new RelayContext("kimi-k2.6");
        // candidates is null — filter should not NPE
        RelayContext result = filter.apply(ctx);
        assertThat(result.candidates()).isNull();
    }

    // --- helpers ---

    private static Vendor vendor(int id, String name) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setName(name);
        return v;
    }

    private static RoutedVendor rv(int instanceId, Vendor vendor) {
        return rv(instanceId, vendor, "");
    }

    private static RoutedVendor rv(int instanceId, Vendor vendor, String tags) {
        return new RoutedVendor(
            vendor,
            "model-" + instanceId,
            "upstream-" + instanceId,
            instanceId,
            tags,
            "{}",
            1
        , 0f, "payg");
    }

    private static RelayContext ctx(RoutedVendor... candidates) {
        RelayContext c = new RelayContext("kimi-k2.6");
        c.setCandidates(List.of(candidates));
        return c;
    }
}
