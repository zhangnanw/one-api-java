package com.oneapi.controller;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
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
     * Send a JSON success response with array data payload.
     */
    protected void ok(RoutingContext ctx, JsonArray data) {
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

    /**
     * Log and respond with a 500 "Database error" response.
     * Eliminates repeated try-catch boilerplate across write operations.
     */
    protected void dbError(RoutingContext ctx, RuntimeException e, String action) {
        log.error("{} failed: {}", action, e.getMessage(), e);
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", false)
                .put("message", "Database error")
                .toString());
    }

    /**
     * Force-read body and validate it exists and is valid JSON.
     * Returns the parsed JsonObject, or null (with an error response already sent).
     */
    protected JsonObject requireBody(RoutingContext ctx) {
        ctx.body(); // force read body buffer
        if (ctx.getBody() == null) {
            badRequest(ctx, "request body is required");
            return null;
        }
        try {
            var body = ctx.getBody().toJsonObject();
            if (body == null) {
                badRequest(ctx, "invalid JSON body");
                return null;
            }
            return body;
        } catch (Exception e) {
            badRequest(ctx, "invalid JSON body");
            return null;
        }
    }

    /**
     * Parse an integer path parameter; return null and send 400 on failure.
     */
    protected Integer parseIntParam(RoutingContext ctx, String param) {
        String value = ctx.pathParam(param);
        if (value == null || value.isEmpty()) {
            badRequest(ctx, param + " is required");
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            badRequest(ctx, param + " must be an integer");
            return null;
        }
    }
}
