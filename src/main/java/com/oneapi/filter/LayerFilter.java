package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 — Filter candidates by layer condition from MatchRule.
 * Only active when matchRule is LayerMatch.
 */
public class LayerFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(LayerFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.LayerMatch lm)) {
            return ctx; // No layer condition — pass through
        }

        String requiredLayer = lm.layer();
        if (requiredLayer == null || requiredLayer.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                MetaView mv = MetaView.fromInstanceMeta(rv.instanceMeta());
                return requiredLayer.equals(mv.instanceLayer());
            })
            .toList();

        log.debug("LayerFilter layer={}: {} → {} candidates",
            requiredLayer, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
