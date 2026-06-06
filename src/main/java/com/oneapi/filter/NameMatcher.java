package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.repo.InstanceRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段 2 — 检查 requestedModel 是否为物理模型名称。
 * 如果在 instances 表中找到，则设置 ctx.matchedPhysical(true)
 * 并跳过虚拟模型查找。
 */
public class NameMatcher implements Filter {
    private static final Logger log = LoggerFactory.getLogger(NameMatcher.class);

    private final InstanceRepo instanceRepo;
    private final boolean requireVirtualModel;

    public NameMatcher(InstanceRepo instanceRepo, boolean requireVirtualModel) {
        this.instanceRepo = instanceRepo;
        this.requireVirtualModel = requireVirtualModel;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        if (instanceRepo.existsByModelName(model)) {
            // 严格模式：物理 model_name 命中 instances 表也不允许直通
            // 设计：API 表面只暴露虚拟模型，不暴露具体实例
            if (requireVirtualModel) {
                log.debug("NameMatcher: {} is a physical model, but strict mode forbids direct use", model);
                // 不设 matchedPhysical，让 VirtualModelLookup 处理（最终会 404）
                return ctx;
            }
            log.debug("NameMatcher: {} is a physical model", model);
            ctx.setMatchedPhysical(true);
            ctx.setMatchRule(new MatchRule.AllMatch());
            ctx.setUpstreamModel(model);
        }

        return ctx;
    }
}
