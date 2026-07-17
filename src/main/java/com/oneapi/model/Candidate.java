package com.oneapi.model;

import com.oneapi.entity.Instance;
import com.oneapi.entity.Vendor;
import io.vertx.core.MultiMap;

/**
 * 解析后的中继候选 — 供应商 + 实例对。
 */
public record Candidate(
    Vendor vendor,
    Instance instance,
    String upstreamModel,
    MultiMap extraHeaders
) {
    public Candidate(Vendor vendor, Instance instance, String upstreamModel) {
        this(vendor, instance, upstreamModel, MultiMap.caseInsensitiveMultiMap());
    }
}
