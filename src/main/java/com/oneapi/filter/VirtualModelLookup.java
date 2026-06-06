package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.model.MatchRule;
import com.oneapi.model.MatchRuleParser;
import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段 2 — 按名称查找虚拟模型，解析匹配规则。
 * 去除 {@code -max} 后缀（按配置）并设置 context.reasoning=true。
 * 如果 ctx.matchedPhysical 为 true，则不执行任何操作。
 * 数据库未命中时，回退到 AllMatch（物理模型直通）。
 */
public class VirtualModelLookup implements Filter {
    private static final Logger log = LoggerFactory.getLogger(VirtualModelLookup.class);

    private final VirtualModelRepo vmRepo;
    private final String triggerSuffix;
    private final boolean requireVirtualModel;

    public VirtualModelLookup(VirtualModelRepo vmRepo, String triggerSuffix, boolean requireVirtualModel) {
        this.vmRepo = vmRepo;
        this.triggerSuffix = triggerSuffix;
        this.requireVirtualModel = requireVirtualModel;
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
        if (virtualModel == null || virtualModel == VirtualModel.NOT_FOUND) {
            // 严格模式：未命中虚拟模型 → 404，禁止物理 model_name 兜底直通
            // 设计：API 表面只暴露虚拟模型，不暴露具体实例
            if (requireVirtualModel) {
                log.info("VirtualModelLookup: {} not registered, reject (strict mode)", lookupName);
                ctx.markError(new RelayError.ModelNotFound(lookupName),
                    "model not registered: " + lookupName);
                return ctx;
            }
            // 旧行为：fallback 到 AllMatch，物理名直通（仅在 requireVirtualModel=false 时）
            log.debug("VirtualModelLookup: {} not found, fallback to AllMatch", lookupName);
            ctx.setMatchRule(new MatchRule.AllMatch());
            ctx.setUpstreamModel(lookupName);
            return ctx;
        }

        // 将匹配 JSON 解析为类型化的 MatchRule
        MatchRule rule = MatchRuleParser.parse(virtualModel.getMatch());
        ctx.setMatchRule(rule);

        // 如果存在 NameMatch，从中提取上游模型名称
        if (rule instanceof MatchRule.NameMatch(String modelName)) {
            ctx.setUpstreamModel(modelName);
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
