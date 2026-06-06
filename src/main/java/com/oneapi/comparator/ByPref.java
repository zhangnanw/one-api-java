package com.oneapi.comparator;

import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 按 pref 升序排列（pref 越小优先级越高）。
 *
 * 排序 = 基础 pref + layer 偏移：
 *   free         +0
 *   subscription +10000
 *   payg         +20000
 *
 * 同层内按基础 pref 区分优先级。
 */
public class ByPref implements Comparator<RoutedVendor> {

    private static int layerOffset(String layer) {
        if (layer == null) return 20000; // 未知按按量处理
        return switch (layer) {
            case "free"         -> 0;
            case "subscription" -> 10000;
            default             -> 20000; // payg
        };
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        MetaView ma = MetaView.fromInstanceMeta(a.instanceMeta());
        MetaView mb = MetaView.fromInstanceMeta(b.instanceMeta());
        int pa = ma.instancePref() + layerOffset(ma.instanceLayer());
        int pb = mb.instancePref() + layerOffset(mb.instanceLayer());
        return Integer.compare(pa, pb);
    }
}
