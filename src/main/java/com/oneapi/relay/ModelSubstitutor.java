package com.oneapi.relay;

import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;

/**
 * Replaces the {@code "model"} field in a JSON request body with the upstream model name.
 * <p>
 * Uses {@link JsonObject} parsing to correctly handle escaped quotes and other JSON edge cases
 * that regex-based replacement cannot safely process.
 */
public class ModelSubstitutor {

    /** 替换 JSON 请求体中的 "model" 字段为上游模型名称。 */
    public static byte[] substitute(byte[] rawBody, String upstreamModel, String srcModel) {
        if (upstreamModel == null || upstreamModel.equals(srcModel)) return rawBody;
        try {
            String body = new String(rawBody, StandardCharsets.UTF_8);
            JsonObject json = new JsonObject(body);
            json.put("model", upstreamModel);
            return json.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return rawBody;
        }
    }
}
