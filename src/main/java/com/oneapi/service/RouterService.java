package com.oneapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneapi.model.Instance;
import com.oneapi.model.Vendor;
import com.oneapi.model.VirtualModel;
import com.oneapi.repo.InstanceRepo;
import static com.oneapi.repo.InstanceRepo.STATUS_RAW;
import com.oneapi.repo.VirtualModelRepo;
import com.oneapi.service.FilterUtils.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RouterService {
    private static final Logger log = LoggerFactory.getLogger(RouterService.class);

    private final VirtualModelRepo vmRepo = new VirtualModelRepo();
    private final InstanceRepo instanceRepo = new InstanceRepo();
    private final CooldownService cooldown = new CooldownService();

    // 10s TTL cache (matching Go's cacheTTL)
    private final Cache<String, VirtualModel> vmCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS).build();
    private final Cache<String, List<Instance>> instanceCache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.SECONDS).build();

    // --- RoutedVendor ---

    public record RoutedVendor(
        Vendor vendor,
        String modelName,
        String upstreamModel,
        int instanceId,
        String instanceTags,
        String instanceMeta
    ) {}

    public record FilterConfig(
        List<Filter> filters,
        String layerAllow,
        int maxPref
    ) {}

    // --- Layer ordering (matching Go) ---

    private static final Map<String, Integer> LAYER_ORDER = Map.of(
        "free", 0, "subscription", 1, "payg", 2
    );

    private static int getLayerWeight(String layer) {
        if (layer == null || layer.isEmpty()) return 99;
        return LAYER_ORDER.getOrDefault(layer, 99);
    }

    /**
     * GetBestVendor — core routing algorithm.
     * Returns (best instance, full candidate list, error).
     */
    public Result getBestVendor(String tokenHash, String requestModel, FilterConfig fc) {
        // 1. Load all instances (cached)
        List<Instance> allInstances = getCachedInstances();
        if (allInstances.isEmpty()) {
            return new Result(null, List.of(), "no instances available");
        }

        // 2. Apply filter chain
        List<Instance> candidates = new ArrayList<>();
        for (Instance inst : allInstances) {
            if (inst.getVendor() == null) continue;
            if (inst.getStatus() == InstanceRepo.STATUS_DISABLED
                || inst.getStatus() == InstanceRepo.STATUS_DEPRECATED) {
                continue;
            }
            boolean pass = true;
            for (Filter f : fc.filters()) {
                if (!f.accept(inst)) {
                    pass = false;
                    break;
                }
            }
            if (pass) {
                candidates.add(inst);
            }
        }

        if (candidates.isEmpty()) {
            log.warn("no candidates after filter chain for model={}", requestModel);
            return new Result(null, List.of(), "all vendors busy");
        }

        // 3. Sort: non-raw first, then layer (free→sub→payg), then pref, then id
        sortInstances(candidates);

        // 4. Pick first non-cooling instance
        List<RoutedVendor> rvs = candidates.stream()
            .map(i -> new RoutedVendor(
                i.getVendor(),
                i.getModelName(),
                i.getUpstreamModel(),
                i.getId(),
                String.join(",", FilterUtils.parseTags(i.getMeta())),
                i.getMeta()
            ))
            .toList();

        // 4.5 Apply layer and max_pref filters (RoutedVendor-level)
        if (fc.layerAllow() != null && !fc.layerAllow().isEmpty()) {
            rvs = FilterUtils.applyLayer(rvs, fc.layerAllow());
        }
        if (fc.maxPref() < Integer.MAX_VALUE) {
            rvs = FilterUtils.applyMaxPref(rvs, fc.maxPref());
        }

        for (RoutedVendor rv : rvs) {
            if (cooldown.isInstanceInCooldown(rv.instanceId, rv.instanceTags)
                || cooldown.isVendorInCooldown(rv.vendor.getId())) {
                continue;
            }
            log.info("picked instance={} vendor={} model={}", rv.instanceId, rv.vendor.getName(), rv.upstreamModel);
            return new Result(rv, rvs, null);
        }

        return new Result(null, rvs, "all vendors busy");
    }

    /**
     * GetNextCandidate — for retry loop.
     * Scans candidates from idx, returns first non-cooling one.
     */
    public CandidateResult getNextCandidate(List<RoutedVendor> candidates, int idx) {
        for (; idx < candidates.size(); idx++) {
            var next = candidates.get(idx);
            if (next.vendor == null) {
                return new CandidateResult(null, idx + 1, "data integrity error");
            }
            if (cooldown.isInstanceInCooldown(next.instanceId, next.instanceTags)
                || cooldown.isVendorInCooldown(next.vendor.getId())) {
                continue;
            }
            return new CandidateResult(next, idx + 1, null);
        }
        return new CandidateResult(null, idx, "all vendors busy");
    }

    /**
     * Build filters + layer/max_pref for a virtual model name.
     */
    public FilterConfig buildFilters(String modelName) {
        VirtualModel vm = getCachedVM(modelName);
        if (vm == null) return new FilterConfig(List.of(), null, Integer.MAX_VALUE);
        String matchJson = vm.getMatch();
        List<Filter> filters = FilterUtils.fromMatchJson(matchJson);
        String layerAllow = FilterUtils.parseMatchLayer(matchJson);
        int maxPref = FilterUtils.parseMatchMaxPref(matchJson, Integer.MAX_VALUE);
        return new FilterConfig(filters, layerAllow, maxPref);
    }

    // --- cooldown proxy methods ---

    public void vendorCooldown(int vendorId) { cooldown.setVendorCooldown(vendorId); }
    public void instanceCooldown(int instId, String tags) { cooldown.setInstanceCooldown(instId, tags); }
    public void clearCooldown(int instId, int vendorId) { cooldown.clearCooldown(instId, vendorId); }

    // --- caching ---

    private VirtualModel getCachedVM(String name) {
        return vmCache.get(name, k -> vmRepo.findByName(k));
    }

    private List<Instance> getCachedInstances() {
        return instanceCache.get("all", k -> instanceRepo.findAllWithVendor());
    }

    // --- sorting ---

    private void sortInstances(List<Instance> instances) {
        // Pre-parse metas for O(n) not O(n log n)
        Map<Integer, List<String>> tagCache = new HashMap<>();
        Map<Integer, Map<String, Object>> metaCache = new HashMap<>();
        for (Instance inst : instances) {
            tagCache.put(inst.getId(), FilterUtils.parseTags(inst.getMeta()));
            metaCache.put(inst.getId(), FilterUtils.parseMeta(inst.getMeta()));
        }

        instances.sort((a, b) -> {
            // Raw instances last
            boolean aRaw = a.getStatus() == STATUS_RAW;
            boolean bRaw = b.getStatus() == STATUS_RAW;
            if (aRaw != bRaw) return aRaw ? 1 : -1;

            // Vendor layer
            String aVLayer = a.getVendor() != null ? parseVendorLayer(a.getVendor().getMeta()) : "";
            String bVLayer = b.getVendor() != null ? parseVendorLayer(b.getVendor().getMeta()) : "";
            int la = getLayerWeight(aVLayer);
            int lb = getLayerWeight(bVLayer);
            if (la != lb) return la - lb;

            // Instance layer (fallback to vendor)
            String aiLayer = metaLayer(metaCache.get(a.getId()), aVLayer);
            String biLayer = metaLayer(metaCache.get(b.getId()), bVLayer);
            int lia = getLayerWeight(aiLayer);
            int lib = getLayerWeight(biLayer);
            if (lia != lib) return lia - lib;

            // Pref
            double pa = metaPref(metaCache.get(a.getId()));
            double pb = metaPref(metaCache.get(b.getId()));
            if (pa != pb) return Double.compare(pa, pb);

            // ID tiebreaker
            return Integer.compare(a.getId(), b.getId());
        });
    }

    @SuppressWarnings("unchecked")
    private static String parseVendorLayer(String meta) {
        try {
            return (String) FilterUtils.parseMeta(meta).get("layer");
        } catch (Exception e) { return ""; }
    }

    private static String metaLayer(Map<String, Object> meta, String fallback) {
        Object layer = meta.get("layer");
        return layer != null ? layer.toString() : fallback;
    }

    private static double metaPref(Map<String, Object> meta) {
        Object pref = meta.get("pref");
        if (pref instanceof Number n) return n.doubleValue();
        return 1000; // default
    }

    // --- Result records ---

    public record Result(RoutedVendor best, List<RoutedVendor> candidates, String error) {
        public boolean isOk() { return error == null && best != null; }
    }

    public record CandidateResult(RoutedVendor candidate, int nextIdx, String error) {
        public boolean isOk() { return error == null && candidate != null; }
    }
}
