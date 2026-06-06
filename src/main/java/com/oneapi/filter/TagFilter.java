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
 * 闃舵 3 鈥?鏍规嵁 MatchRule.TagMatch 鐨勬爣绛炬潯浠惰繃婊ゅ€欓€夈€?
 * 鏀寔 ALL锛堝繀椤诲寘鍚墍鏈夋爣绛撅級鍜?ANY锛堝繀椤诲寘鍚嚦灏戜竴涓級銆?
 */
public class TagFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(TagFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.TagMatch(Set<String> allTags, Set<String> anyTags))) {
            return ctx; // 鏃犳爣绛炬潯浠?鈥?鐩存帴閫氳繃
        }

        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> {
                MetaView mv = MetaView.fromInstanceMeta(routedVendor.instanceMeta());
                // ALL 鏉′欢锛氬疄渚嬪繀椤诲寘鍚墍鏈夎姹傜殑鏍囩
                if (allTags != null && !allTags.isEmpty()) {
                    for (String tag : allTags) {
                        if (!mv.instanceHasTag(tag)) {
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
                        return false;
                    }
                }
                return true;
            })
            .toList();

        log.debug("TagFilter: {} 鈫?{} candidates", candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
