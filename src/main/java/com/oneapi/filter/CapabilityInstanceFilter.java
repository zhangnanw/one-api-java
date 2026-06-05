package com.oneapi.filter;

import com.oneapi.model.MetaView;
import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 — Filter candidates by capability requirement (set by CapabilityFilter in Stage 2).
 * Checks instance meta for matching "capability:&lt;name&gt;" tag.
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
