package com.oneapi.comparator;

import com.oneapi.config.AppConfig;
import com.oneapi.core.RouterService.RoutedVendor;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the {@link Comparator} chain for sorting {@link RoutedVendor} candidates.
 * <p>
 * Order: ByScore (layer-based pref offset) → ByStatusDesc (higher status first).
 */
public class SorterFactory {

    public static Comparator<RoutedVendor> build(AppConfig config) {
        List<String> layerOrder = config.getRelay() != null
            ? config.getRelay().getLayerOrder()
            : List.of("free", "subscription", "payg");
        return new ByScore(layerOrder)
            .thenComparing(new ByStatusDesc());
    }
}
