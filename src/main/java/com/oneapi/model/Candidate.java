package com.oneapi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 解析后的中继候选 — 供应商 + 实例对。
 */
public record Candidate(
    Vendor vendor,
    Instance instance,
    String upstreamModel,
    Map<String, String> extraHeaders
) {
    public Candidate(Vendor vendor, Instance instance, String upstreamModel) {
        this(vendor, instance, upstreamModel, new HashMap<>());
    }
}
