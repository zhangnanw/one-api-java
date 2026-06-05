package com.oneapi.model;

/**
 * Relay result (stage 5 output).
 */
public record RelayResult(
    int httpStatus,
    String responseBody,
    String actualModel,
    int promptTokens,
    int completionTokens
) {}
