package com.oneapi.controller;

import com.oneapi.coordinator.RelayCoordinator;
import io.vertx.ext.web.RoutingContext;

/**
 * 薄 HTTP 边界 — 仅负责读取 Vert.x 请求体并委托给协调器。
 * <p>
 * 职责边界（重要，避免误读）：
 * <ul>
 *   <li>本类唯一职责：从 {@link RoutingContext} 读取原始 body 字节，调用 {@link RelayCoordinator#execute}</li>
 *   <li>不做模型名解析、过滤、排序、转发 —— 这些全部在 {@link RelayCoordinator} 中</li>
 *   <li>HTTP 状态码、错误响应格式由协调器负责（见 {@code RelayCoordinator.error}）</li>
 * </ul>
 * <p>
 * 注意：上一版本注释说"不做解析"易被误读为"不做任何 JSON / body 解析"。
 * 实际仅指不做模型名解析，body 读取仍是本类的职责。
 */
public class RelayControllerV2 {
    private final RelayCoordinator coordinator;

    public RelayControllerV2(RelayCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    public void handle(RoutingContext ctx) {
        ctx.request().bodyHandler(buf -> coordinator.execute(ctx, buf.getBytes()));
    }
}
