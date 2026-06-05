package com.oneapi.model;

import java.util.Set;

/**
 * Parsed instance capabilities from meta JSON column.
 */
public record InstanceCaps(
    Set<String> tags,    // e.g. ["capability:reasoning"]
    String layer,        // "free" | "subscription" | "payg" | ""
    int maxPref          // priority upper bound, 0 = unlimited
) {
    public static InstanceCaps empty() {
        return new InstanceCaps(Set.of(), "", 0);
    }
    
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
}
