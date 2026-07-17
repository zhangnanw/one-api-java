package com.oneapi.filter;

import com.oneapi.repository.InstanceRepository;
import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import lombok.extern.slf4j.Slf4j;

/**
 * 阶段 2 �?检�?requestedModel 是否为物理模型名称�? * API 表面只暴露虚拟模型，不暴露具体实例�? */
@Slf4j
public class NameMatcher implements Filter {

    private final InstanceRepository instanceRepo;
    private final boolean requireVirtualModel;

    public NameMatcher(InstanceRepository instanceRepo) {
        this(instanceRepo, true);
    }

    public NameMatcher(InstanceRepository instanceRepo, boolean requireVirtualModel) {
        this.instanceRepo = instanceRepo;
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

        if (instanceRepo.existsByModelNameAndStatusIn(model,
                java.util.List.of(com.oneapi.entity.Instance.STATUS_RAW, com.oneapi.entity.Instance.STATUS_TAGGED))) {
            log.info("NameMatcher: {} is a physical model name, direct use forbidden", model);
            ctx.setMatchedPhysical(true);
            ctx.markError(new RelayError.DirectUseForbidden(model),
                "model " + model + " is a physical model name; "
                    + "API only accepts virtual model names. Register a virtual model that maps to it.");
        }
        return ctx;
    }
}
