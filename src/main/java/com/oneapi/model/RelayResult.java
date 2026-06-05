package com.oneapi.model;

/**
 * 中继结果（阶段5输出）。
 */
public record RelayResult(
    int httpStatus,
    String responseBody,
    String actualModel,
    int promptTokens,
    int completionTokens
) {}
