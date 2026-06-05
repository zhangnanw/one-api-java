package com.oneapi.controller;

import com.oneapi.coordinator.RelayCoordinator;
import io.vertx.ext.web.RoutingContext;

/**
 * 薄 HTTP 边界 — 接收请求体，委托给协调器。
 * 不做解析、过滤、排序或转发。所有逻辑均在 RelayCoordinator 中。
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
