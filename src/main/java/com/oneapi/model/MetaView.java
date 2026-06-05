package com.oneapi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * 将供应商/实例 meta JSON 一次性解析为类型化记录。
 * 构造函数解析一次；所有 getter 从缓存结果读取。
 */
public class MetaView {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    private final VendorCaps vendorCaps;
    private final InstanceCaps instanceCaps;
    private final String raw;
    
    // 解析供应商 meta
    public MetaView(Vendor vendor) {
        this.raw = vendor.getMeta();
        this.vendorCaps = parseVendorMeta(this.raw);
        this.instanceCaps = null;
    }
    
    // 解析实例 meta
    public MetaView(Instance instance) {
        this.raw = instance.getMeta();
        this.instanceCaps = parseInstanceMeta(this.raw);
        this.vendorCaps = null;
    }
    
    // 已解析（用于供应商+实例组合）
    private MetaView(VendorCaps vc, InstanceCaps ic) {
        this.vendorCaps = vc;
        this.instanceCaps = ic;
        this.raw = null;
    }
    
    public static MetaView of(Vendor v) { return new MetaView(v); }
    public static MetaView of(Instance i) { return new MetaView(i); }
    public static MetaView of(VendorCaps vc, InstanceCaps ic) { return new MetaView(vc, ic); }

    /** 解析实例 meta JSON 字符串（用于 RoutedVendor 等） */
    public static MetaView fromInstanceMeta(String metaJson) {
        return new MetaView(null, parseInstanceMeta(metaJson));
    }

    /** 解析供应商 meta JSON 字符串（用于 RoutedVendor 等） */
    public static MetaView fromVendorMeta(String metaJson) {
        return new MetaView(parseVendorMeta(metaJson), null);
    }
    
    // --- 供应商访问器 ---
    public String vendorLayer() { return vendorCaps != null ? vendorCaps.layer() : ""; }
    public Set<String> vendorTags() { return vendorCaps != null ? vendorCaps.tags() : Set.of(); }
    public boolean vendorNoCool() { return vendorCaps != null && vendorCaps.nocool(); }
    public boolean vendorHasTag(String tag) { return vendorCaps != null && vendorCaps.hasTag(tag); }
    
    // --- 实例访问器 ---
    public String instanceLayer() { return instanceCaps != null ? instanceCaps.layer() : ""; }
    public Set<String> instanceTags() { return instanceCaps != null ? instanceCaps.tags() : Set.of(); }
    public int instanceMaxPref() { return instanceCaps != null ? instanceCaps.maxPref() : 0; }
    public boolean instanceHasTag(String tag) { return instanceCaps != null && instanceCaps.hasTag(tag); }
    
    // --- 解析辅助方法（私有，每个 MetaView 调用一次） ---
    
    @SuppressWarnings("unchecked")
    private static VendorCaps parseVendorMeta(String meta) {
        if (meta == null || meta.isEmpty()) return VendorCaps.empty();
        try {
            Map<String, Object> m = mapper.readValue(meta, Map.class);
            String layer = m.getOrDefault("layer", "").toString();
            Set<String> tags = parseTagsArray(m.get("tags"));
            boolean nocool = "true".equals(String.valueOf(m.getOrDefault("nocool", "false")));
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
            Set<String> tags = parseTagsArray(m.get("tags"));
            String layer = m.getOrDefault("layer", "").toString();
            int maxPref = m.get("max_pref") instanceof Number n ? n.intValue() : 0;
            return new InstanceCaps(tags, layer, maxPref);
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
