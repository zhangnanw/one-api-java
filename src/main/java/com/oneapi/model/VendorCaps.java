package com.oneapi.model;

import java.util.Set;

/**
 * 从 meta JSON 列解析出的供应商能力。
 * 一次解析，到处使用。
 */
public record VendorCaps(
    String layer,        // "free" | "subscription" | "payg" | "" 
    Set<String> tags,    // 例如 ["capability:reasoning", "capability:vision"]  
    boolean nocool       // 冷却豁免
) {
    public static VendorCaps empty() {
        return new VendorCaps("", Set.of(), false);
    }
    
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
}
