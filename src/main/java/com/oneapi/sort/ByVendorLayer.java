package com.oneapi.sort;

import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 第四阶段 — 按供应商层优先级排序。
 * 默认顺序：free (0) &lt; subscription (1) &lt; payg (2)。
 * 未知层取 MAX_VALUE（最低优先级）。
 */
public class ByVendorLayer implements Comparator<RoutedVendor> {
    private final Map<String, Integer> layerOrder;

    public ByVendorLayer(List<String> layerOrderList) {
        this.layerOrder = new HashMap<>();
        for (int i = 0; i < layerOrderList.size(); i++) {
            layerOrder.put(layerOrderList.get(i), i);
        }
    }

    /** 默认顺序：free &lt; subscription &lt; payg */
    public ByVendorLayer() {
        this(List.of("free", "subscription", "payg"));
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        int wa = layerWeight(a);
        int wb = layerWeight(b);
        return Integer.compare(wa, wb);
    }

    private int layerWeight(RoutedVendor rv) {
        if (rv.vendor() == null) return Integer.MAX_VALUE;
        MetaView mv = MetaView.fromVendorMeta(rv.vendor().getMeta());
        String layer = mv.vendorLayer();
        if (layer == null || layer.isEmpty()) return Integer.MAX_VALUE;
        return layerOrder.getOrDefault(layer, Integer.MAX_VALUE);
    }
}
