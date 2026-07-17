package com.oneapi.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneapi.repository.InstanceRepository;
import com.oneapi.model.Candidate;
import com.oneapi.entity.Instance;
import com.oneapi.entity.Vendor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RouterService {
    private final InstanceRepository instanceRepo;
    private final CooldownService cooldownService;
    private final FilterUtils filterUtils;

    public RouterService(InstanceRepository instanceRepo, CooldownService cooldownService,
                         FilterUtils filterUtils) {
        this.instanceRepo = instanceRepo;
        this.cooldownService = cooldownService;
        this.filterUtils = filterUtils;
    }

    // 60 秒 TTL 缓存（避免频繁查库）
    private final Cache<String, List<Instance>> instanceCache = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS).build();

    public record RoutedVendor(
        Vendor vendor,
        String modelName,
        String upstreamModel,
        int instanceId,
        String instanceTags,
        String instanceMeta,
        int instanceStatus,
        float instancePref,
        String instanceLayer
    ) {
        public Candidate toCandidate() {
            Instance instance = new Instance();
            instance.setId(instanceId);
            instance.setModelName(modelName);
            instance.setUpstreamModel(upstreamModel);
            instance.setVendor(vendor);
            instance.setStatus(instanceStatus);
            instance.setMeta(instanceMeta);
            return new Candidate(vendor, instance, upstreamModel);
        }
    }

    public List<RoutedVendor> loadCandidates(String modelName) {
        List<Instance> all = getCachedInstances();
        if (all.isEmpty()) return List.of();

        List<RoutedVendor> candidates = all.stream()
            .filter(i -> i.getVendor() != null)
            .filter(i -> i.getModelName() != null && i.getModelName().equals(modelName))
            .filter(i -> i.getStatus() != Instance.STATUS_DISABLED
                      && i.getStatus() != Instance.STATUS_DEPRECATED
                      && i.getStatus() != Instance.STATUS_FAILED
                      && i.getStatus() != Instance.STATUS_UNKNOWN)
            .map(i -> new RoutedVendor(
                i.getVendor(),
                i.getModelName(),
                i.getUpstreamModel(),
                i.getId(),
                String.join(",", filterUtils.parseTags(i.getMeta())),
                i.getMeta(),
                i.getStatus(),
                i.getPref(),
                i.getLayer()
            ))
            .toList();

        return candidates.stream()
            .filter(rv -> {
                boolean instCool = cooldownService.isInstanceInCooldown(rv.instanceId(), rv.instanceTags());
                boolean vendCool = rv.vendor() != null
                    && cooldownService.isVendorInCooldown(rv.vendor().getId());
                return !instCool && !vendCool;
            })
            .toList();
    }

    private List<Instance> getCachedInstances() {
        return instanceCache.get("all", k -> instanceRepo.findAllWithVendor(
            List.of(Instance.STATUS_DISABLED, Instance.STATUS_DEPRECATED, Instance.STATUS_FAILED, Instance.STATUS_UNKNOWN)));
    }
}
