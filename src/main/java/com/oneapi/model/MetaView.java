package com.oneapi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * 一次性解析供应商/实例 meta JSON 为类型化记录。
 * 构造函数解析一次；所有访问器从缓存结果读取。
 *
 * 入口：
 * - {@link #of(Vendor)} — 供应商实体
 * - {@link #of(Instance)} — 实例实体
 * - {@link #fromInstanceMeta(String)} — 实例 meta 字符串（用于 RoutedVendor 没有实体的场景）
 */
public class MetaView {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final VendorCaps vendorCaps;
    private final InstanceCaps instanceCaps;

    private MetaView(VendorCaps vc, InstanceCaps ic) {
        this.vendorCaps = vc;
        this.instanceCaps = ic;
    }

    public static MetaView of(Vendor vendor) {
        return new MetaView(parseVendorMeta(vendor.getMeta()), null);
    }

    public static MetaView of(Instance instance) {
        return new MetaView(null, parseInstanceMeta(instance.getMeta()));
    }

    /**
     * 解析实例 meta JSON 字符串。
     * 仅在调用方拿不到 Instance 实体时使用（例如 RoutedVendor）。
     */
    public static MetaView fromInstanceMeta(String metaJson) {
        return new MetaView(null, parseInstanceMeta(metaJson));
    }

    // --- 供应商访问器 ---
    public String vendorLayer() { return vendorCaps != null ? vendorCaps.layer() : ""; }
    public Set<String> vendorTags() { return vendorCaps != null ? vendorCaps.tags() : Set.of(); }
    public boolean vendorNoCool() { return vendorCaps != null && vendorCaps.nocool(); }
    public boolean vendorHasTag(String tag) { return vendorCaps != null && vendorCaps.hasTag(tag); }

    // --- 实例访问器 ---
    public String instanceLayer() { return instanceCaps != null ? instanceCaps.layer() : ""; }
    public Set<String> instanceTags() { return instanceCaps != null ? instanceCaps.tags() : Set.of(); }
    public float instancePref() { return instanceCaps != null ? instanceCaps.pref() : 0f; }
    public int instanceMaxTokens() { return instanceCaps != null ? instanceCaps.maxTokens() : 0; }
    public InstanceCaps instanceCaps() { return instanceCaps != null ? instanceCaps : InstanceCaps.empty(); }
    public boolean instanceHasTag(String tag) { return instanceCaps != null && instanceCaps.hasTag(tag); }

    // --- 解析辅助方法（私有，每个 MetaView 调用一次） ---

    @SuppressWarnings("unchecked")
    private static VendorCaps parseVendorMeta(String meta) {
        if (meta == null || meta.isEmpty()) return VendorCaps.empty();
        try {
            Map<String, Object> m = mapper.readValue(meta, Map.class);
            String layer = m.getOrDefault(MetaKeys.LAYER, "").toString();
            Set<String> tags = parseTagsArray(m.get(MetaKeys.TAGS));
            boolean nocool = "true".equals(String.valueOf(m.getOrDefault(MetaKeys.NOCOOL, "false")));
            return new VendorCaps(layer, tags, nocool);
        } catch (Exception e) {
            return VendorCaps.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static InstanceCaps parseInstanceMeta(String meta) {
        if (meta == null || meta.isEmpty()) return InstanceCaps.empty();
        try {
            Map<String, Object> m = mapper.readValue(meta, Map.class);
            Set<String> tags = parseTagsArray(m.get(MetaKeys.TAGS));
            String layer = m.getOrDefault(MetaKeys.LAYER, "").toString();
            float pref = m.get(MetaKeys.PREF) instanceof Number n ? n.floatValue() : 0f;
            int maxTokens = m.get(MetaKeys.MAX_TOKENS) instanceof Number n ? n.intValue() : 0;
            return new InstanceCaps(tags, layer, pref, maxTokens);
        } catch (Exception e) {
            return InstanceCaps.empty();
        }
    }

    private static Set<String> parseTagsArray(Object tagsNode) {
        if (tagsNode instanceof List<?> list) {
            Set<String> tags = new HashSet<>();
            for (Object t : list) tags.add(t.toString());
            return Collections.unmodifiableSet(tags);
        }
        return Set.of();
    }
}
