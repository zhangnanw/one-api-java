package com.oneapi.controller;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class MiscController {

    public void status(RoutingContext ctx) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", new JsonObject()
                    .put("start_time", System.currentTimeMillis() / 1000)
                    .put("system_name", "one-api-java")
                    .put("version", "1.0.0"))
                .toString());
    }
}
