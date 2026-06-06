package com.oneapi.controller;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Shared response helpers for the CRUD controllers.
 * <p>
 * 共享响应工具：用于 Vendor / Instance / VirtualModel / Misc 等 CRUD 控制器。
 * <p>
 * Response envelope is always:
 * <pre>
 *   { "success": boolean, "message": string, "data": object (optional) }
 * </pre>
 */
public class BaseController {

    /**
     * Send a JSON response. {@code status < 400} maps to {@code success=true};
     * otherwise {@code success=false}.
     */
    protected void json(RoutingContext ctx, int status, String msg) {
        ctx.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", status < 400)
                .put("message", msg)
                .toString());
    }

    /**
     * Send a JSON success response with data payload.
     */
    protected void ok(RoutingContext ctx, JsonObject data) {
        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", data)
                .toString());
    }

    /**
     * Send a JSON success response (no data).
     */
    protected void ok(RoutingContext ctx) {
        json(ctx, 200, "");
    }

    /**
     * Send a JSON error response.
     */
    protected void err(RoutingContext ctx, int status, String msg) {
        json(ctx, status, msg);
    }

    /**
     * 400 with a message.
     */
    protected void badRequest(RoutingContext ctx, String msg) {
        json(ctx, 400, msg);
    }

    /**
     * 404 with a message.
     */
    protected void notFound(RoutingContext ctx, String entity) {
        json(ctx, 404, entity + " not found");
    }
}
