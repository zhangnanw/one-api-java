package com.oneapi.controller;

import com.oneapi.coordinator.RelayCoordinator;
import io.vertx.ext.web.RoutingContext;

/**
 * Thin HTTP boundary — receives body, delegates to coordinator.
 * Does NOT parse, filter, sort, or relay. All logic lives in RelayCoordinator.
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
