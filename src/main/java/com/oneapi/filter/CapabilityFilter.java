package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 2 — If MatchRule requires a capability string, set marker on ctx.
 * The actual capability-based filtering happens in Stage 3.
 */
public class CapabilityFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(CapabilityFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        MatchRule rule = ctx.matchRule();
        if (rule instanceof MatchRule.CapabilityMatch cm) {
            ctx.setCapabilityRequired(cm.capability());
            log.debug("CapabilityFilter: requiring capability={}", cm.capability());
        }
        return ctx;
    }
}
