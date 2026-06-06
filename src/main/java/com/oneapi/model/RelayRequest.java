package com.oneapi.model;

import io.vertx.core.http.HttpServerResponse;

/**
 * 域模型：解析后的入站中继请求（阶段1输出）。
 * <p>
 * Domain model for the parsed inbound relay request (stage 1 output).
 * <p>
 * Boundary: this is the in-process domain object passed through the filter chain.
 * For the actual outbound HTTP call to the upstream vendor, see
 * {@link com.oneapi.handler.UpstreamClient.OutboundRequest}.
 * <p>
 * Body string is derived via {@code new String(rawBody)} when needed; not stored
 * separately.
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
