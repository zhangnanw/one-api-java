package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.repo.InstanceRepo;
import lombok.extern.slf4j.Slf4j;

/**
 * 阶段 2 — 检查 requestedModel 是否为物理模型名称。
 * API 表面只暴露虚拟模型，不暴露具体实例。
 * <p>
 * 行为：
 * <ul>
 *   <li>{@code relay.requireVirtualModel = true}（默认）→ requestedModel 命中 {@code instances.model_name}
 *       时标记 {@code matchedPhysical=true}，并写入 {@link RelayError#DirectUseForbidden}（HTTP 404 + 明确说明）</li>
 *   <li>{@code relay.requireVirtualModel = false} → 跳过物理模型检查；NameMatcher 仅透传 ctx</li>
 * </ul>
 * <p>
 * 后续 filter（{@code VirtualModelLookup}）看到 {@code matchedPhysical=true} 会短路跳过，
 * 协调器（{@code RelayCoordinator}）看到 {@code hasError()=true} 会在 stage2 之后立即返回。
 * <p>
 * 这覆盖了 {@code requirements §B.2 "API 不暴露具体实例"} 的需求——开关 {@code relay.requireVirtualModel}
 * 决定是否启用，对应 README 中的同名配置。
 */
@Slf4j
public class NameMatcher implements Filter {

    private final InstanceRepo instanceRepo;
    private final boolean requireVirtualModel;

    /** 默认启用 requireVirtualModel（保留原行为）。 */
    public NameMatcher(InstanceRepo instanceRepo) {
        this(instanceRepo, true);
    }

    public NameMatcher(InstanceRepo instanceRepo, boolean requireVirtualModel) {
        this.instanceRepo = instanceRepo;
        this.requireVirtualModel = requireVirtualModel;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        if (!requireVirtualModel) {
            // 关闭了"强制走虚拟模型"策略：透传
            return ctx;
        }
        String model = ctx.requestedModel();
        if (model == null || model.isEmpty()) {
            return ctx;
        }

        if (instanceRepo.existsByModelName(model)) {
            log.info("NameMatcher: {} is a physical model name, direct use forbidden", model);
            ctx.setMatchedPhysical(true);
            ctx.markError(new RelayError.DirectUseForbidden(model),
                "model " + model + " is a physical model name; "
                    + "API only accepts virtual model names. Register a virtual model that maps to it.");
        }
        return ctx;
    }
}
