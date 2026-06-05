package com.oneapi.sort;

import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Stage 4 — Sort by instance layer priority.
 * Default order: free (0) < subscription (1) < payg (2).
 */
public class ByInstanceLayer implements Comparator<RoutedVendor> {
    private final Map<String, Integer> layerOrder;

    public ByInstanceLayer(List<String> layerOrderList) {
        this.layerOrder = new HashMap<>();
        for (int i = 0; i < layerOrderList.size(); i++) {
            layerOrder.put(layerOrderList.get(i), i);
        }
    }

    /** Default: free < subscription < payg */
    public ByInstanceLayer() {
        this(List.of("free", "subscription", "payg"));
    }

    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        int wa = layerWeight(a);
        int wb = layerWeight(b);
        return Integer.compare(wa, wb);
    }

    private int layerWeight(RoutedVendor rv) {
        MetaView mv = MetaView.fromInstanceMeta(rv.instanceMeta());
        String layer = mv.instanceLayer();
        if (layer == null || layer.isEmpty()) return Integer.MAX_VALUE;
        return layerOrder.getOrDefault(layer, Integer.MAX_VALUE);
    }
}
