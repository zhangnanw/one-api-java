package com.oneapi.model;

import io.vertx.core.json.JsonObject;

/**
 * Static helpers for extracting error information from {@link RelayException}
 * and mapping HTTP status codes to error type strings.
 */
public class RelayErrorHelper {

    public static int extractHttpStatus(Throwable err) {
        if (err instanceof RelayException re
                && re.getError() instanceof RelayError.UpstreamFailure upstreamFailure) {
            return upstreamFailure.httpCode();
        }
        return 503;
    }

    /**
     * 提取上游错误消息。仅在所有候选耗尽后暴露给客户端。
     * 有备用实例时，此消息仅写入日志，不返回给客户端。
     */
    public static String extractErrorMessage(Throwable err) {
        if (err instanceof RelayException re
                && re.getError() instanceof RelayError.UpstreamFailure upstreamFailure) {
            String body = upstreamFailure.responseBody();
            if (body != null) {
                try {
                    var json = new JsonObject(body);
                    var errorNode = json.getJsonObject("error");
                    if (errorNode != null && errorNode.getString("message") != null) {
                        return "upstream: " + errorNode.getString("message");
                    }
                } catch (Exception ignored) {}
            }
            return "upstream " + upstreamFailure.httpCode();
        }
        return err.getMessage() != null ? err.getMessage() : "relay failed";
    }

    public static String errorTypeFromStatus(int status) {
        return switch (status) {
            case 429 -> "rate_limited";
            case 403 -> "forbidden";
            case 502 -> "bad_gateway";
            case 503 -> "service_unavailable";
            case 504 -> "gateway_timeout";
            case 200 -> "empty_response";
            default -> "http_" + status;
        };
    }
}
