package com.oneapi.comparator;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;
import java.util.List;

/**
 * 按分数升序排列（值越小优先级越高）。
 *
 * score = layerBase + pref
 *   layerBase: 由 config.layerOrder 列表索引决定（索引越小优先级越高）
 *   pref:      理论上 0~1 浮点数，同层内区分；代码不强制夹紧，越界值仍可排序
 *
 * 后续叠加项：标签权重（多层级就近覆盖：实例 > 供应商 > 逻辑模型）、
 * 统计分（余额、故障率等），都合并到 pref 槽位。
 */
public class ByScore implements Comparator<RoutedVendor> {

    private final List<String> layerOrder;

    /** Default: free < subscription < payg. */
    public ByScore() {
        this(List.of("free", "subscription", "payg"));
    }

    public ByScore(List<String> layerOrder) {
        this.layerOrder = layerOrder != null && !layerOrder.isEmpty()
            ? layerOrder
            : List.of("free", "subscription", "payg");
    }

    private float layerBase(String layer) {
        if (layer == null || layer.isEmpty()) {
            return (float) layerOrder.size(); // 未知 layer = 最后
        }
        int idx = layerOrder.indexOf(layer);
        return idx >= 0 ? (float) idx : (float) layerOrder.size();
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        float pa = layerBase(a.instanceLayer()) + a.instancePref();
        float pb = layerBase(b.instanceLayer()) + b.instancePref();
        return Float.compare(pa, pb);
    }
}
