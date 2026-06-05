package com.oneapi.sort;

import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * Stage 4 — Sort by max_pref descending (higher pref = better).
 */
public class ByPref implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        int pa = MetaView.fromInstanceMeta(a.instanceMeta()).instanceMaxPref();
        int pb = MetaView.fromInstanceMeta(b.instanceMeta()).instanceMaxPref();
        // Descending: higher pref first
        return Integer.compare(pb, pa);
    }
}
