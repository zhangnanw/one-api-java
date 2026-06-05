package com.oneapi.middleware;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

/**
 * CORS middleware — allows cross-origin requests.
 * Matches Go's middleware/cors.go.
 *
 * Allows all origins, methods, and headers for API access from
 * any web frontend or management panel.
 */
public class CORS implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext ctx) {
        ctx.response()
            .putHeader("Access-Control-Allow-Origin", "*")
            .putHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS,PATCH")
            .putHeader("Access-Control-Allow-Headers",
                "Authorization,Content-Type,X-Requested-With,Accept,User-Agent")
            .putHeader("Access-Control-Allow-Credentials", "true")
            .putHeader("Access-Control-Max-Age", "86400");

        // Handle preflight (OPTIONS) requests
        if (ctx.request().method() == HttpMethod.OPTIONS) {
            ctx.response().setStatusCode(204).end();
            return;
        }

        ctx.next();
    }
}
