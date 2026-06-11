package com.oneapi.model;

import io.vertx.core.http.HttpServerResponse;

/**
 * 中继请求 — 阶段 1（请求解析）的输出，封装客户端发来的原始请求。
 * <p>
 * 由 {@link com.oneapi.coordinator.RequestParser} 从 HTTP body 解析得到。
 * 在过滤器链和协调器中传递，不直接用于发往上游（发往上游用 {@link com.oneapi.handler.UpstreamClient.OutboundRequest}）。
 * <p>
 * rawBody 保留原始字节，避免在解析/修改 body 时出现编码问题。
 * 需要字符串形式时，调用方自行 {@code new String(rawBody, StandardCharsets.UTF_8)}。
 * <p>
 * sink 字段仅用于流式请求，指向客户端的 HTTP 响应流。非流式请求为 null。
 */
public record RelayRequest(
    String requestedModel,
    byte[] rawBody,
    boolean streaming,
    HttpServerResponse sink  // 可为空，用于流式管道
) {
    public RelayRequest(String requestedModel, byte[] rawBody, boolean streaming) {
        this(requestedModel, rawBody, streaming, null);
    }
}
