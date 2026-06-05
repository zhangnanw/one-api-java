package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MatchRuleParser;
import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 2 — Look up virtual model by name, parse match rules.
 * Strips {@code -max} suffix (per config) and sets context.reasoning=true.
 * If ctx.matchedPhysical is true, this is a no-op.
 * On DB miss, falls back to AllMatch (physical model passthrough).
 */
public class VirtualModelLookup implements Filter {
    private static final Logger log = LoggerFactory.getLogger(VirtualModelLookup.class);

    private final VirtualModelRepo vmRepo;
    private final String triggerSuffix;

    public VirtualModelLookup(VirtualModelRepo vmRepo, String triggerSuffix) {
        this.vmRepo = vmRepo;
        this.triggerSuffix = triggerSuffix;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        // Already matched as physical — skip
        if (ctx.matchedPhysical()) {
            return ctx;
        }

        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        // Strip -max suffix for virtual model lookup
        boolean reasoning = false;
        String lookupName = model;
        if (triggerSuffix != null && !triggerSuffix.isEmpty() && model.endsWith(triggerSuffix)) {
            lookupName = model.substring(0, model.length() - triggerSuffix.length());
            reasoning = true;
            log.debug("VirtualModelLookup: stripped suffix {} → {}, reasoning=true",
                triggerSuffix, lookupName);
        }

        VirtualModel vm = vmRepo.findByName(lookupName);
        if (vm == null || vm == VirtualModel.NOT_FOUND) {
            log.debug("VirtualModelLookup: {} not found, fallback to AllMatch", lookupName);
            ctx.setMatchRule(new MatchRule.AllMatch());
            ctx.setUpstreamModel(lookupName);
            return ctx;
        }

        // Parse match JSON into typed MatchRule
        MatchRule rule = MatchRuleParser.parse(vm.getMatch());
        ctx.setMatchRule(rule);

        // Extract upstream model name from NameMatch if present
        if (rule instanceof MatchRule.NameMatch nm) {
            ctx.setUpstreamModel(nm.modelName());
        } else {
            ctx.setUpstreamModel(lookupName);
        }

        if (reasoning) {
            ctx.setReasoning(true);
        }

        log.debug("VirtualModelLookup: {} → {} (rule={})", model, ctx.upstreamModel(),
            rule.getClass().getSimpleName());
        return ctx;
    }
}
