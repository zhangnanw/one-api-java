package com.oneapi.filter;

import com.oneapi.model.RelayContext;
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

        VirtualModel vm = vmRepo.findByName(lookupName);
        if (vm == null || vm == VirtualModel.NOT_FOUND) {
            log.debug("VirtualModelLookup: {} not found, fallback to AllMatch", lookupName);
            ctx.setMatchRule(new MatchRule.AllMatch());
            ctx.setUpstreamModel(lookupName);
            return ctx;
        }

        // 将匹配 JSON 解析为类型化的 MatchRule
        MatchRule rule = MatchRuleParser.parse(vm.getMatch());
        ctx.setMatchRule(rule);

        // 如果存在 NameMatch，从中提取上游模型名称
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
