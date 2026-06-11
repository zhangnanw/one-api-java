package com.oneapi.comparator;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 按优先级升序排列（值越小优先级越高）。
 *
 * priority = layer + pref
 *   layer: free=0.0, subscription=1.0, payg=2.0
 *   pref:  0~1 浮点数，同层内区分
 *
 * 软标签/用量等后续叠加到 pref 中。
 */
public class ByPref implements Comparator<RoutedVendor> {

    private static float layerBase(String layer) {
        if (layer == null || layer.isEmpty()) return 2.0f;
        return switch (layer) {
            case "free"         -> 0.0f;
            case "subscription" -> 1.0f;
            default             -> 2.0f;
        };
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        float pa = layerBase(a.instanceLayer()) + a.instancePref();
        float pb = layerBase(b.instanceLayer()) + b.instancePref();
        return Float.compare(pa, pb);
    }
}
