package com.oneapi.comparator;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;
import java.util.List;

/**
 * 按分数升序排列（值越小优先级越高）。
 *
 * <p>score = (layerIndex × SCALE) + round(pref × SCALE)
 *   layerIndex: 由 config.layerOrder 列表索引决定（索引越小优先级越高）
 *   pref:      0~1 浮点数，同层内区分
 *   SCALE:     1000（消除 float 比较抖动）
 *
 * <p>后续叠加项：标签权重（多层级就近覆盖：实例 > 供应商 > 逻辑模型）、
 * 统计分（余额、故障率等），都合并到 pref 槽位。
 */
public class ByScore implements Comparator<RoutedVendor> {

    private static final int SCALE = 1000;

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

    private int layerIndex(String layer) {
        if (layer == null || layer.isEmpty()) {
            return layerOrder.size(); // 未知 layer = 最后
        }
        int idx = layerOrder.indexOf(layer);
        return idx >= 0 ? idx : layerOrder.size();
    }

    /**
     * 计算稳定的排序分数。
     * 先取 layerIndex × SCALE（整数），再加 round(pref × SCALE)；
     * 结果为 long，消除了 float 比较时的 NaN / -0.0 / 精度损失抖动。
     */
    private long score(RoutedVendor v) {
        return ((long) layerIndex(v.instanceLayer())) * SCALE
             + Math.round(v.instancePref() * SCALE);
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        return Long.compare(score(a), score(b));
    }
}
