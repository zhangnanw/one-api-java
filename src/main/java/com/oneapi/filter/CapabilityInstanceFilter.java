package com.oneapi.filter;

import com.oneapi.model.MetaView;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 阶段 3 — 根据能力需求（由阶段 2 的 CapabilityFilter 设置）过滤候选。
 * 检查实例元数据中是否包含匹配的 "capability:&lt;名称&gt;" 标签。
 */
public class CapabilityInstanceFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(CapabilityInstanceFilter.class);

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

        String targetTag = "capability:" + required;
        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                MetaView mv = MetaView.fromInstanceMeta(rv.instanceMeta());
                return mv != null && mv.instanceHasTag(targetTag);
            })
            .toList();

        log.debug("CapabilityInstanceFilter {}: {} → {} candidates",
            required, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
