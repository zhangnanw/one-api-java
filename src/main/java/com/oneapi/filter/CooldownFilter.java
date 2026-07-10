package com.oneapi.filter;

import com.oneapi.model.RelayContext;

import java.util.ArrayList;
import com.oneapi.service.CooldownService;
import com.oneapi.service.RouterService.RoutedVendor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 阶段 3 — 移除实例或供应商处于冷却期的候选。
 */
@Slf4j
public class CooldownFilter implements Filter {

    private final CooldownService cooldown;

    public CooldownFilter(CooldownService cooldown) {
        this.cooldown = cooldown;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<Integer> removedIds = new ArrayList<>();
        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> {
                boolean instanceCool = cooldown.isInstanceInCooldown(
                    routedVendor.instanceId(), routedVendor.instanceTags());
                boolean vendorCool = routedVendor.vendor() != null
                    && cooldown.isVendorInCooldown(routedVendor.vendor().getId());
                if (instanceCool || vendorCool) {
                    removedIds.add(routedVendor.instanceId());
                    log.debug("CooldownFilter: skip instance={} vendor={}",
                        routedVendor.instanceId(), routedVendor.vendor() != null ? routedVendor.vendor().getName() : "null");
                    return false;
                }
                return true;
            })
            .toList();

        if (!removedIds.isEmpty()) {
            String reason = "in cooldown";
            ctx.addFilterAction("CooldownFilter", candidates.size(), filtered.size(), removedIds, reason);
        }

        log.debug("CooldownFilter: {} -> {} candidates",
            candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
