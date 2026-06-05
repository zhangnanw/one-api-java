package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.service.CooldownService;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 — Exclude candidates whose instance or vendor is on cooldown.
 */
public class CoolingFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(CoolingFilter.class);

    private final CooldownService cooldown;

    public CoolingFilter(CooldownService cooldown) {
        this.cooldown = cooldown;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                boolean instanceCool = cooldown.isInstanceInCooldown(
                    rv.instanceId(), rv.instanceTags());
                boolean vendorCool = cooldown.isVendorInCooldown(
                    rv.vendor() != null ? rv.vendor().getId() : 0);
                if (instanceCool || vendorCool) {
                    log.debug("CoolingFilter: skip instance={} vendor={}",
                        rv.instanceId(), rv.vendor() != null ? rv.vendor().getName() : "null");
                    return false;
                }
                return true;
            })
            .toList();

        log.debug("CoolingFilter: {} → {} candidates",
            candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
