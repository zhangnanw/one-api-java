package com.oneapi.comparator;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 按分数升序排列（值越小优先级越高）。
 *
 * score = layer + pref
 *   layer: free=0.0, subscription=1.0, payg=2.0
 *   pref:  理论上 0~1 浮点数，同层内区分；代码不强制夹紧，越界值仍可排序
 *
 * 后续叠加项：标签权重（多层级就近覆盖：实例 > 供应商 > 逻辑模型）、
 * 统计分（余额、故障率等），都合并到 pref 槽位。
 */
public class ByScore implements Comparator<RoutedVendor> {

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
