package com.oneapi.sort;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 第四阶段 — 稳定决胜：按实例 ID 升序排列。
 */
public class ById implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        return Integer.compare(a.instanceId(), b.instanceId());
    }
}
