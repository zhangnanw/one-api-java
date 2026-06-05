package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段 2 — 如果 MatchRule 要求能力字符串，则在 ctx 上设置标记。
 * 实际基于能力的过滤在阶段 3 中进行。
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
