package com.oneapi.sort;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * Stage 4 — Stable tiebreaker: sort by instance id ascending.
 */
public class ById implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        return Integer.compare(a.instanceId(), b.instanceId());
    }
}
