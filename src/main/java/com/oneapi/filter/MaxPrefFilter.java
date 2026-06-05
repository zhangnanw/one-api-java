package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 阶段 3 — 仅保留 max_pref 值最高的候选。
 * 如果所有候选的 max_pref 均为 0，则全部通过。
 */
public class MaxPrefFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(MaxPrefFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        // 查找最高的 max_pref
        int highest = candidates.stream()
            .mapToInt(rv -> MetaView.fromInstanceMeta(rv.instanceMeta()).instanceMaxPref())
            .max()
            .orElse(0);

        if (highest <= 0) {
            return ctx; // 无有意义的 max_pref — 全部通过
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> MetaView.fromInstanceMeta(rv.instanceMeta()).instanceMaxPref() == highest)
            .toList();

        log.debug("MaxPrefFilter maxPref={}: {} → {} candidates",
            highest, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
