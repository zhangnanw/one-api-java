package com.oneapi.coordinator;

import com.oneapi.model.RelayRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Stage 1 — Parse HTTP body into RelayRequest.
 * Returns null if body is empty or model cannot be determined.
 */
public class RequestParser {
    public static RelayRequest parse(RoutingContext ctx, byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) return null;

        String bodyStr = new String(rawBody);
        String model = ctx.request().getParam("model");
        if (model == null) {
            try {
                model = new JsonObject(bodyStr).getString("model");
            } catch (Exception e) { /* ignore */ }
        }
        if (model == null || model.isEmpty()) return null;

        boolean isStream = false;
        try {
            isStream = new JsonObject(bodyStr).getBoolean("stream", false);
        } catch (Exception e) { /* ignore */ }

        return new RelayRequest(model, rawBody, bodyStr, isStream);
    }
}
