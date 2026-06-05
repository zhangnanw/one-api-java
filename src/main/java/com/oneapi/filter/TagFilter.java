package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * 阶段 3 — 根据 MatchRule.TagMatch 的标签条件过滤候选。
 * 支持 ALL（必须包含所有标签）和 ANY（必须包含至少一个）。
 */
public class TagFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(TagFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.TagMatch tm)) {
            return ctx; // 无标签条件 — 直接通过
        }

        Set<String> allTags = tm.all();
        Set<String> anyTags = tm.any();

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                MetaView mv = MetaView.fromInstanceMeta(rv.instanceMeta());
                // ALL 条件：实例必须包含所有要求的标签
                if (allTags != null && !allTags.isEmpty()) {
                    for (String tag : allTags) {
                        if (!mv.instanceHasTag(tag)) {
                            return false;
                        }
                    }
                }
                // ANY 条件：实例必须包含至少一个
                if (anyTags != null && !anyTags.isEmpty()) {
                    boolean hasAny = false;
                    for (String tag : anyTags) {
                        if (mv.instanceHasTag(tag)) {
                            hasAny = true;
                            break;
                        }
                    }
                    if (!hasAny) {
                        return false;
                    }
                }
                return true;
            })
            .toList();

        log.debug("TagFilter: {} → {} candidates", candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
