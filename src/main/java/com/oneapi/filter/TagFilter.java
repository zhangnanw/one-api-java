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
 * Stage 3 — Filter candidates by tag conditions from MatchRule.TagMatch.
 * Supports ALL (must have all tags) and ANY (must have at least one).
 */
public class TagFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(TagFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (!(rule instanceof MatchRule.TagMatch tm)) {
            return ctx; // No tag conditions — pass through
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
                // ALL condition: instance must have every required tag
                if (allTags != null && !allTags.isEmpty()) {
                    for (String tag : allTags) {
                        if (!mv.instanceHasTag(tag)) {
                            return false;
                        }
                    }
                }
                // ANY condition: instance must have at least one
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
