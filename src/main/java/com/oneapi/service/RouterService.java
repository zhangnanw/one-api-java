package com.oneapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneapi.model.Instance;
import com.oneapi.model.Vendor;
import com.oneapi.repo.InstanceRepo;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class RouterService {
    private final InstanceRepo instanceRepo = new InstanceRepo();

    // 10s TTL cache (matching Go's cacheTTL)
    private final Cache<String, List<Instance>> instanceCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS).build();

    // --- RoutedVendor ---

    public record RoutedVendor(
        Vendor vendor,
        String modelName,
        String upstreamModel,
        int instanceId,
        String instanceTags,
        String instanceMeta,
        int instanceStatus
    ) {}

    /**
     * Load raw candidates for given model — no filtering, no sorting.
     * Used by V2 coordinator which runs its own filter chain.
     * Returns all instances with model_name matching the requested model.
     */
    public List<RoutedVendor> loadCandidates(String modelName) {
        List<Instance> all = getCachedInstances();
        if (all.isEmpty()) return List.of();

        return all.stream()
            .filter(i -> i.getVendor() != null)
            .filter(i -> i.getModelName() != null && i.getModelName().equals(modelName))
            .filter(i -> i.getStatus() != InstanceRepo.STATUS_DISABLED
                      && i.getStatus() != InstanceRepo.STATUS_DEPRECATED)
            .map(i -> new RoutedVendor(
                i.getVendor(),
                i.getModelName(),
                i.getUpstreamModel(),
                i.getId(),
                String.join(",", FilterUtils.parseTags(i.getMeta())),
                i.getMeta(),
                i.getStatus()
            ))
            .toList();
    }

    // --- caching ---

    private List<Instance> getCachedInstances() {
        return instanceCache.get("all", k -> instanceRepo.findAllWithVendor());
    }
}
