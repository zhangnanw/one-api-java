package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.repo.ModelCatalogRepo;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 阶段 3 — 根据能力需求筛选候选实例。
 * <p>
 * 使用 {@link ModelCatalogRepo} 检查实例对应的模型画像是否具备所需能力。
 * 例如：请求包含 image_url → VisionFilter 设置 capabilityRequired="vision"
 * → 本 filter 过滤掉 model_catalog 中无 "vision" 能力的模型的所有实例。
 */
public class CapabilityInstanceFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(CapabilityInstanceFilter.class);

    private final ModelCatalogRepo catalogRepo;

    public CapabilityInstanceFilter(ModelCatalogRepo catalogRepo) {
        this.catalogRepo = catalogRepo;
    }

    /**
     * For backward-compatible wiring only. Falls back to no-op filtering.
     * Use {@link #CapabilityInstanceFilter(ModelCatalogRepo)} for full capability support.
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

        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> catalogRepo.hasCapability(rv.modelName(), required))
            .toList();

        log.debug("CapabilityInstanceFilter {}: {} → {} candidates",
            required, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
