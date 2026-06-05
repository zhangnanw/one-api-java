package com.oneapi.sort;

import com.oneapi.repo.InstanceRepo;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * Stage 4 — Sort raw (STATUS_RAW=1) instances after tagged (STATUS_TAGGED=2).
 * Lower status values sort last so raw instances are deprioritized.
 */
public class RawStatusLast implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        // STATUS_TAGGED (2) > STATUS_RAW (1): higher status = better priority
        // Reverse: tagged(2) comes first, raw(1) comes last
        return Integer.compare(b.instanceStatus(), a.instanceStatus());
    }
}
