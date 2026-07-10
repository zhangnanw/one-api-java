package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MatchRuleParser;
import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import lombok.extern.slf4j.Slf4j;

/**
 * 阶段 2 — 按名称查找虚拟模型，解析匹配规则。
 * 去除 {@code -max} 后缀（按配置）并设置 context.reasoning=true。
 * 如果 ctx.matchedPhysical 为 true，则不执行任何操作。
 * 数据库未命中 → 直接 404。
 */
@Slf4j
public class VirtualModelLookup implements Filter {

    private final VirtualModelRepo vmRepo;
    private final String triggerSuffix;

    public VirtualModelLookup(VirtualModelRepo vmRepo, String triggerSuffix) {
        this.vmRepo = vmRepo;
        this.triggerSuffix = triggerSuffix;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        // 已匹配为物理模型 — 跳过
        if (ctx.matchedPhysical()) {
            return ctx;
        }

        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        // 去除 -max 后缀用于虚拟模型查找
        boolean reasoning = false;
        String lookupName = model;
        if (triggerSuffix != null && !triggerSuffix.isEmpty() && model.endsWith(triggerSuffix)) {
            lookupName = model.substring(0, model.length() - triggerSuffix.length());
            reasoning = true;
            log.debug("VirtualModelLookup: stripped suffix {} → {}, reasoning=true",
                triggerSuffix, lookupName);
        }

        VirtualModel virtualModel = vmRepo.findByName(lookupName);
        if (virtualModel == null) {
            log.info("VirtualModelLookup: {} not registered, reject", lookupName);
            ctx.markError(new RelayError.ModelNotFound(lookupName),
                "model not registered: " + lookupName);
            return ctx;
        }

        // 将匹配 JSON 解析为类型化的 MatchRule
        MatchRule rule = MatchRuleParser.parse(virtualModel.getMatch());
        ctx.setMatchRule(rule);

        // 设置路由模型名称
        // ModelsMatch：设 modelNames，不设路由模型名（RelayCoordinator 以此为准）
        if (rule instanceof MatchRule.ModelsMatch mm) {
            ctx.setModelNames(mm.modelNames());
        } else if (rule instanceof MatchRule.NameMatch nm) {
            ctx.setRoutingModelName(nm.modelName());
        } else {
            ctx.setRoutingModelName(lookupName);
        }

        if (reasoning) {
            ctx.setReasoning(true);
        }

        if (rule instanceof MatchRule.ModelsMatch mm) {
            log.debug("VirtualModelLookup: {} → {} (rule=ModelsMatch, {} models)",
                model, lookupName, mm.modelNames().size());
        } else {
            log.debug("VirtualModelLookup: {} → {} (rule={})", model, ctx.routingModelName(),
                rule.getClass().getSimpleName());
        }
        return ctx;
    }
}
