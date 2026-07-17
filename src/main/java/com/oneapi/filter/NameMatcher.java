package com.oneapi.filter;

import com.oneapi.service.InstanceService;
import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import lombok.extern.slf4j.Slf4j;

/**
 * 阶段 2：检查 requestedModel 是否为物理模型名称。
 * API 表面只暴露虚拟模型，不暴露具体实例。
 */
@Slf4j
public class NameMatcher implements Filter {

    private final InstanceService instanceService;
    private final boolean requireVirtualModel;

    public NameMatcher(InstanceService instanceService) {
        this(instanceService, true);
    }

    public NameMatcher(InstanceService instanceService, boolean requireVirtualModel) {
        this.instanceService = instanceService;
        this.requireVirtualModel = requireVirtualModel;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        if (!requireVirtualModel) {
            return ctx;
        }
        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        if (instanceService.existsByModelName(model)) {
            log.info("NameMatcher: {} is a physical model name, direct use forbidden", model);
            ctx.setMatchedPhysical(true);
            ctx.markError(new RelayError.DirectUseForbidden(model),
                "model " + model + " is a physical model name; "
                    + "API only accepts virtual model names. Register a virtual model that maps to it.");
        }
        return ctx;
    }
}
