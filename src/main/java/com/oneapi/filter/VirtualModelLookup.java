package com.oneapi.filter;

import com.oneapi.jpa.VirtualModelJpaRepository;
import com.oneapi.model.MatchRule;
import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.model.MatchRuleParser;
import com.oneapi.model.VirtualModel;
import lombok.extern.slf4j.Slf4j;

/**
 * 阶段 2 — 按名称查找虚拟模型，解析匹配规则。
 * 去除 {@code -max} 后缀（按配置）并设置 context.reasoning=true。
 * 如果 ctx.matchedPhysical 为 true，则不执行任何操作。
 * 数据库未命中 → 直接 404。
 */
@Slf4j
public class VirtualModelLookup implements Filter {

    private final VirtualModelJpaRepository vmRepo;
    private final String triggerSuffix;

    public VirtualModelLookup(VirtualModelJpaRepository vmRepo, String triggerSuffix) {
        this.vmRepo = vmRepo;
        this.triggerSuffix = triggerSuffix;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        if (ctx.matchedPhysical()) {
            return ctx;
        }

        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        boolean reasoning = false;
        String lookupName = model;
        if (triggerSuffix != null && !triggerSuffix.isEmpty() && model.endsWith(triggerSuffix)) {
            lookupName = model.substring(0, model.length() - triggerSuffix.length());
            reasoning = true;
            log.debug("VirtualModelLookup: stripped suffix {} → {}, reasoning=true",
                triggerSuffix, lookupName);
        }

        VirtualModel virtualModel = vmRepo.findByName(lookupName).orElse(null);
        if (virtualModel == null) {
            log.info("VirtualModelLookup: {} not registered, reject", lookupName);
            ctx.markError(new RelayError.ModelNotFound(lookupName),
                "model not registered: " + lookupName);
            return ctx;
        }

        MatchRule rule = MatchRuleParser.parse(virtualModel.getMatch());
        ctx.setMatchRule(rule);
        if (rule instanceof MatchRule.ModelsMatch mm) {
            ctx.setModelNames(mm.modelNames());
        } else if (rule instanceof MatchRule.NameMatch nm) {
            ctx.setRoutingModelName(nm.modelName());
        }
        ctx.setReasoning(reasoning);
        return ctx;
    }
}
