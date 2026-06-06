package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 闃舵 3 鈥?鏍规嵁 MatchRule 鐨勫眰绾ф潯浠惰繃婊ゅ€欓€夈€?
 * 浠呭湪 matchRule 涓?LayerMatch 鏃剁敓鏁堛€?
 */
public class LayerFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(LayerFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.LayerMatch(String requiredLayer))) {
            return ctx; // 鏃犲眰绾ф潯浠?鈥?鐩存帴閫氳繃
        }

        if (requiredLayer == null || requiredLayer.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> {
                MetaView mv = MetaView.fromInstanceMeta(routedVendor.instanceMeta());
                return requiredLayer.equals(mv.instanceLayer());
            })
            .toList();

        log.debug("LayerFilter layer={}: {} 鈫?{} candidates",
            requiredLayer, candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
