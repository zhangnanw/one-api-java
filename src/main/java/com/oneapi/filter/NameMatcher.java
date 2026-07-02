package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.repo.InstanceRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段 2 — 检查 requestedModel 是否为物理模型名称。
 * API 表面只暴露虚拟模型，不暴露具体实例。
 * <p>
 * 行为：
 * <ul>
 *   <li>requestedModel 命中 {@code instances.model_name} → 标记 {@code matchedPhysical=true}，
 *       并写入 {@link RelayError#DirectUseForbidden} 错误（HTTP 404 + 明确说明）</li>
 *   <li>非命中 → 透传，交给 {@link VirtualModelLookup} 查虚拟模型表</li>
 * </ul>
 * <p>
 * 后续 filter（{@code VirtualModelLookup}）看到 {@code matchedPhysical=true} 会短路跳过，
 * 协调器（{@code RelayCoordinator}）看到 {@code hasError()=true} 会在 stage2 之后立即返回。
 * <p>
 * 这覆盖了 {@code requirements §B.2 "API 不暴露具体实例"} 的需求——之前该 filter 仅打 debug log，
 * 物理模型名仍会落库查询并 404，但错误信息模糊（"model not registered"）。现在直接拒绝。
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
            log.info("NameMatcher: {} is a physical model name, direct use forbidden", model);
            ctx.setMatchedPhysical(true);
            ctx.markError(new RelayError.DirectUseForbidden(model),
                "model " + model + " is a physical model name; "
                    + "API only accepts virtual model names. Register a virtual model that maps to it.");
        }
        return ctx;
    }
}