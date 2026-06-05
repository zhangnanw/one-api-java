package com.oneapi.model;

import io.vertx.core.http.HttpServerResponse;

/**
 * 解析后的中继请求（阶段1输出）。
 */
public record RelayRequest(
    String requestedModel,
    byte[] rawBody,
    String bodyString,
    boolean isStreaming,
    HttpServerResponse sink  // 可为空，用于流式管道
) {
    public RelayRequest(String requestedModel, byte[] rawBody, String bodyString, boolean isStreaming) {
        this(requestedModel, rawBody, bodyString, isStreaming, null);
    }
}
