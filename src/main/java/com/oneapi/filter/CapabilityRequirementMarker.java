package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段 2 — 仅标记 ctx.capabilityRequired，不筛选。
 * 实际基于能力的过滤在阶段 3 由 {@link CapabilityInstanceFilter} 负责。
 */
public class CapabilityRequirementMarker implements Filter {
    private static final Logger log = LoggerFactory.getLogger(CapabilityRequirementMarker.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (rule instanceof MatchRule.CapabilityMatch cm) {
            String capability = cm.capability();
            ctx.setCapabilityRequired(capability);
            log.debug("CapabilityRequirementMarker: requiring capability={}", capability);
        }
        return ctx;
    }
}
