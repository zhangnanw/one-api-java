package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.MatchRule;
import com.oneapi.repo.InstanceRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段 2 — 检查 requestedModel 是否为物理模型名称。
 * 物理模型名命中 instances 表时，不设任何 ctx 字段，交给
 * VirtualModelLookup 处理（最终 404）。
 * API 表面只暴露虚拟模型，不暴露具体实例。
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
            log.debug("NameMatcher: {} is a physical model, direct use forbidden", model);
        }

        return ctx;
    }
}
