package com.oneapi.model;

import java.util.Set;

/**
 * 从 meta JSON 列解析出的实例能力。
 */
public record InstanceCaps(
    Set<String> tags,    // 例如 ["capability:reasoning"]
    String layer,        // "free" | "subscription" | "payg" | ""
    int maxPref          // 优先级上限，0 = 无限制
) {
    public static InstanceCaps empty() {
        return new InstanceCaps(Set.of(), "", 0);
    }
    
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }
}
