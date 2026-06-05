package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.repo.InstanceRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 2 — Check if requestedModel is a physical model name.
 * If found in instances table, sets ctx.matchedPhysical(true)
 * and short-circuits virtual model lookup.
 */
public class NameMatcher implements Filter {
    private static final Logger log = LoggerFactory.getLogger(NameMatcher.class);

    private final InstanceRepo instanceRepo;

    public NameMatcher(InstanceRepo instanceRepo) {
        this.instanceRepo = instanceRepo;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        if (instanceRepo.existsByModelName(model)) {
            log.debug("NameMatcher: {} is a physical model", model);
            ctx.setMatchedPhysical(true);
            ctx.setMatchRule(new MatchRule.AllMatch());
            ctx.setUpstreamModel(model);
        }

        return ctx;
    }
}
