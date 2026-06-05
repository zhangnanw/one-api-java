package com.oneapi.model;

import java.util.Set;

/**
 * Parsed vendor capabilities from meta JSON column.
 * Parse once, use everywhere.
 */
public record VendorCaps(
    String layer,        // "free" | "subscription" | "payg" | "" 
    Set<String> tags,    // e.g. ["capability:reasoning", "capability:vision"]  
    boolean nocool       // cooling exemption
) {
    public static VendorCaps empty() {
        return new VendorCaps("", Set.of(), false);
    }
    
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
}
