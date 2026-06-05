package com.oneapi.sort;

import com.oneapi.model.MetaView;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 第四阶段 — 按 max_pref 降序排列（pref 越高越优）。
 */
public class ByPref implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        int pa = MetaView.fromInstanceMeta(a.instanceMeta()).instanceMaxPref();
        int pb = MetaView.fromInstanceMeta(b.instanceMeta()).instanceMaxPref();
        // 降序：pref 高的在前
        return Integer.compare(pb, pa);
    }
}
