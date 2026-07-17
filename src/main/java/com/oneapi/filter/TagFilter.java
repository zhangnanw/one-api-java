package com.oneapi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.model.RelayContext;

import java.util.ArrayList;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MetaView;
import com.oneapi.core.RouterService.RoutedVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * 阶段 3 — 根据 MatchRule.TagMatch 的标签条件过滤候选。
 * 支持 ALL（必须包含所有标签）和 ANY（必须包含至少一个）。
 */
@Slf4j
@RequiredArgsConstructor
public class TagFilter implements Filter {

    private final ObjectMapper objectMapper;

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.TagMatch tm)) {
            return ctx; // 无标签条件 — 直接通过
        }
        Set<String> allTags = tm.allTags();
        Set<String> anyTags = tm.anyTags();

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<Integer> removedIds = new ArrayList<>();
        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> {
                MetaView mv = MetaView.fromInstanceMeta(objectMapper, routedVendor.instanceMeta());
                // ALL 鏉′欢锛氬疄渚嬪繀椤诲寘鍚墍鏈夎姹傜殑鏍囩
                if (allTags != null && !allTags.isEmpty()) {
                    for (String tag : allTags) {
                        if (!mv.instanceHasTag(tag)) {
                            removedIds.add(routedVendor.instanceId());
                            return false;
                        }
                    }
                }
                // ANY 鏉′欢锛氬疄渚嬪繀椤诲寘鍚嚦灏戜竴涓?
                if (anyTags != null && !anyTags.isEmpty()) {
                    boolean hasAny = false;
                    for (String tag : anyTags) {
                        if (mv.instanceHasTag(tag)) {
                            hasAny = true;
                            break;
                        }
                    }
                    if (!hasAny) {
                        removedIds.add(routedVendor.instanceId());
                        return false;
                    }
                }
                return true;
            })
            .toList();

        if (!removedIds.isEmpty()) {
            String reason = "tag mismatch";
            ctx.addFilterAction("TagFilter", candidates.size(), filtered.size(), removedIds, reason);
        }

        log.debug("TagFilter: {} → {} candidates", candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
