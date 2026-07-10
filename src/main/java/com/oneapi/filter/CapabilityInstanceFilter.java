package com.oneapi.filter;

import com.oneapi.model.RelayContext;

import java.util.ArrayList;
import com.oneapi.repo.CapabilityCatalog;
import com.oneapi.service.RouterService.RoutedVendor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 阶段 3 — 根据能力需求筛选候选实例。
 * <p>
 * 使用 {@link CapabilityCatalog} 检查实例对应的模型画像是否具备所需能力。
 * 例如：请求包含 image_url → VisionFilter 设置 capabilityRequired="vision"
 * → 本 filter 过滤掉 model_catalog 中无 "vision" 能力的模型的所有实例。
 */
@Slf4j
public class CapabilityInstanceFilter implements Filter {

    private final CapabilityCatalog catalogRepo;

    public CapabilityInstanceFilter(CapabilityCatalog catalogRepo) {
        this.catalogRepo = catalogRepo;
    }

    /**
     * For backward-compatible wiring only. Falls back to no-op filtering.
     * Use {@link #CapabilityInstanceFilter(CapabilityCatalog)} for full capability support.
     */
    public CapabilityInstanceFilter() {
        this.catalogRepo = null;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        String required = ctx.capabilityRequired();
        if (required == null || required.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        // No catalog repo wired — keep all candidates (backward compat)
        if (catalogRepo == null) {
            log.debug("CapabilityInstanceFilter {}: skipped (no catalog repo)", required);
            return ctx;
        }

        List<Integer> removedIds = new ArrayList<>();
        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                if (!catalogRepo.hasCapability(rv.modelName(), required)) {
                    removedIds.add(rv.instanceId());
                    return false;
                }
                return true;
            })
            .toList();

        if (!removedIds.isEmpty()) {
            ctx.addFilterAction("CapabilityInstanceFilter", candidates.size(), filtered.size(),
                removedIds, "no capability: " + required);
        }

        log.debug("CapabilityInstanceFilter {}: {} → {} candidates",
            required, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
