package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 阶段 3 — 仅保留活跃实例（STATUS_RAW 或 STATUS_TAGGED）。
 * 排除 DISABLED 和 DEPRECATED。排序（RawStatusLast）处理 RAW 的降优先级。
 */
public class RawStatusFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RawStatusFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> rv.instanceStatus() == InstanceRepo.STATUS_RAW
                       || rv.instanceStatus() == InstanceRepo.STATUS_TAGGED)
            .toList();

        log.debug("RawStatusFilter: {} → {} candidates",
            candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
