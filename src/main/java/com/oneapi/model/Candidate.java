package com.oneapi.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolved relay candidate — vendor + instance pair.
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
