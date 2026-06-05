package com.oneapi.model;

import io.vertx.core.http.HttpServerResponse;

/**
 * Parsed relay request (stage 1 output).
 */
public record RelayRequest(
    String requestedModel,
    byte[] rawBody,
    String bodyString,
    boolean isStreaming,
    HttpServerResponse sink  // nullable, for streaming pipe
) {
    public RelayRequest(String requestedModel, byte[] rawBody, String bodyString, boolean isStreaming) {
        this(requestedModel, rawBody, bodyString, isStreaming, null);
    }
}
