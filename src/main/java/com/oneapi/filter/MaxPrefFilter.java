package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 — Keep only candidates with the highest max_pref value.
 * If all have 0 max_pref, all pass through.
 */
public class MaxPrefFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(MaxPrefFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        // Find the highest max_pref
        int highest = candidates.stream()
            .mapToInt(rv -> MetaView.fromInstanceMeta(rv.instanceMeta()).instanceMaxPref())
            .max()
            .orElse(0);

        if (highest <= 0) {
            return ctx; // No meaningful max_pref — pass all through
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
