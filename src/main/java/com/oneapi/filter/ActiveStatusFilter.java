package com.oneapi.filter;

import com.oneapi.model.Instance;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 阶段 3 — 仅保留活跃实例（STATUS_RAW 或 STATUS_TAGGED），移除 DISABLED 和 DEPRECATED。
 */
public class ActiveStatusFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ActiveStatusFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> routedVendor.instanceStatus() == Instance.STATUS_RAW
                       || routedVendor.instanceStatus() == Instance.STATUS_TAGGED)
            .toList();

        log.debug("ActiveStatusFilter: {} → {} candidates",
            candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
