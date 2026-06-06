package com.oneapi.coordinator;

import com.oneapi.model.RelayRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * 第一阶段 — 将 HTTP 请求体解析为 RelayRequest。
 * 如果请求体为空或无法确定模型则返回 null。
 */
public class RequestParser {
    public static RelayRequest parse(RoutingContext ctx, byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) return null;

        String bodyStr = new String(rawBody);
        String model = ctx.request().getParam("model");
        boolean streaming = false;

        // 解析一次 JSON，同时取 model 和 stream
        if (model == null) {
            try {
                JsonObject json = new JsonObject(bodyStr);
                model = json.getString("model");
                streaming = json.getBoolean("stream", false);
            } catch (Exception e) { /* 忽略 */ }
        } else {
            try {
                streaming = new JsonObject(bodyStr).getBoolean("stream", false);
            } catch (Exception e) { /* 忽略 */ }
        }

        if (model == null || model.isEmpty()) return null;

        return new RelayRequest(model, rawBody, streaming);
    }
}
