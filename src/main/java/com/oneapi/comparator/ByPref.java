package com.oneapi.comparator;

import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 按优先级升序排列（值越小优先级越高）。
 *
 * 排序 = layer 基数 + pref 小数：
 *   free         → 0.x
 *   subscription → 1.x
 *   payg         → 2.x
 *
 * pref 值除以 100000 转为小数部分，同层内区分。
 * 后续软标签（writing/reasoning 等）可叠加到小数位。
 */
public class ByPref implements Comparator<RoutedVendor> {

    /** pref 缩放因子：原 pref 值除以 100000 得到小数部分。 */
    private static final float PREF_SCALE = 100_000f;

    private static float layerBase(String layer) {
        if (layer == null) return 2.0f; // 未知按按量处理
        return switch (layer) {
            case "free"         -> 0.0f;
            case "subscription" -> 1.0f;
            default             -> 2.0f; // payg
        };
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        MetaView ma = MetaView.fromInstanceMeta(a.instanceMeta());
        MetaView mb = MetaView.fromInstanceMeta(b.instanceMeta());
        float pa = layerBase(ma.instanceLayer()) + ma.instancePref() / PREF_SCALE;
        float pb = layerBase(mb.instanceLayer()) + mb.instancePref() / PREF_SCALE;
        return Float.compare(pa, pb);
    }
}
