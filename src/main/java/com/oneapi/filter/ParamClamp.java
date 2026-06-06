package com.oneapi.filter;

import com.oneapi.model.InstanceCaps;
import com.oneapi.model.MetaKeys;
import io.vertx.core.json.JsonObject;

/**
 * Clamp model parameters that exceed upstream limits.
 * Not part of the Filter chain — utility invoked directly by the coordinator.
 *
 * Cap priority: InstanceCaps.maxTokens (from instance meta JSON `max_tokens`)
 * -> DEFAULT_CAP fallback.
 */
public class ParamClamp {

    public static final int DEFAULT_CAP = 16384;

    private ParamClamp() {}

    public static byte[] clamp(byte[] rawBody, InstanceCaps caps) {
        int cap = (caps != null && caps.maxTokens() > 0) ? caps.maxTokens() : DEFAULT_CAP;
        return clampTo(rawBody, cap);
    }

    public static byte[] clamp(byte[] rawBody) {
        return clampTo(rawBody, DEFAULT_CAP);
    }

    private static byte[] clampTo(byte[] rawBody, int cap) {
        try {
            JsonObject body = new JsonObject(new String(rawBody));
            if (!body.containsKey(MetaKeys.MAX_TOKENS)) return rawBody;
            int maxTokens = body.getInteger(MetaKeys.MAX_TOKENS);
            if (maxTokens <= cap) return rawBody;
            body.put(MetaKeys.MAX_TOKENS, cap);
            return body.toString().getBytes();
        } catch (Exception e) {
            return rawBody;
        }
    }
}
