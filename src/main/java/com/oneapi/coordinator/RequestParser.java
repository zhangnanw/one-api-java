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

        // model == null 时：从 body 解析 JSON，提取 model 和 stream
        // model != null 时：按需提取 stream，避免完整解析大 body
        if (model == null) {
            try {
                JsonObject json = new JsonObject(bodyStr);
                model = json.getString("model");
                streaming = json.getBoolean("stream", false);
            } catch (Exception e) { /* 忽略 */ }
        } else {
            // 用 contains 快速判断 stream 字段是否存在，避免无必要的大 body 解析
            try {
                if (bodyStr.contains("\"stream\"")) {
                    streaming = new JsonObject(bodyStr).getBoolean("stream", false);
                }
            } catch (Exception e) { /* 忽略 */ }
        }

        if (model == null || model.isEmpty()) return null;

        return new RelayRequest(model, rawBody, streaming);
    }
}
