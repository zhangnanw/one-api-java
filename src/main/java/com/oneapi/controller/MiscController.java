package com.oneapi.controller;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import com.oneapi.core.CooldownService;

public class MiscController extends BaseController {

    private final long startTime = System.currentTimeMillis() / 1000;
    private final CooldownService cooldown;

    public MiscController(CooldownService cooldown) {
        this.cooldown = cooldown;
    }

    public void status(RoutingContext ctx) {
        var cooldownStats = cooldown.getStats();
        ok(ctx, new JsonObject()
            .put("start_time", startTime)
            .put("system_name", "one-api-java")
            .put("version", "1.0.0")
            .put("cooldown", new JsonObject()
                .put("hitCount", cooldownStats.hitCount())
                .put("missCount", cooldownStats.missCount())
                .put("evictionCount", cooldownStats.evictionCount())
                .put("estimatedSize", cooldownStats.estimatedSize())));
    }
}
