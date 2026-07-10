package com.oneapi.filter;

import com.oneapi.model.RelayContext;

import java.util.ArrayList;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 阶段 3 — 根据 MatchRule 的层级条件过滤候选。
 * 仅在 matchRule 为 LayerMatch 时生效。
 */
public class LayerFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(LayerFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.LayerMatch lm)) {
            return ctx; // 无层级条件 — 直接通过
        }
        String requiredLayer = lm.layer();

        if (requiredLayer == null || requiredLayer.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<Integer> removedIds = new ArrayList<>();
        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> {
                String layer = routedVendor.instanceLayer();
                if (layer == null || layer.isEmpty()) layer = "payg";
                if (!requiredLayer.equals(layer)) {
                    removedIds.add(routedVendor.instanceId());
                    return false;
                }
                return true;
            })
            .toList();

        if (!removedIds.isEmpty()) {
            ctx.addFilterAction("LayerFilter", candidates.size(), filtered.size(),
                removedIds, "layer != " + requiredLayer);
        }

        log.debug("LayerFilter layer={}: {} → {} candidates",
            requiredLayer, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
