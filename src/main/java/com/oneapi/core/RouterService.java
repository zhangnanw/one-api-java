package com.oneapi.core;

import com.oneapi.repository.InstanceRepository;
import com.oneapi.model.Candidate;
import com.oneapi.entity.Instance;
import com.oneapi.entity.Vendor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RouterService {

    private static final String INSTANCE_CACHE = "routedInstances";

    private final InstanceRepository instanceRepo;
    private final CooldownService cooldownService;
    private final FilterUtils filterUtils;

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

    /**
     * 加载可用实例列表（带 Spring 缓存，60 秒 TTL）。
     * 状态过滤和冷却判断都不缓存，因为它们是动态的。
     */
    @Cacheable(value = INSTANCE_CACHE, key = "'all'")
    public List<Instance> getAvailableInstances() {
        return instanceRepo.findAllWithVendor(
            List.of(Instance.STATUS_DISABLED, Instance.STATUS_DEPRECATED,
                Instance.STATUS_FAILED, Instance.STATUS_UNKNOWN));
    }

    public List<RoutedVendor> loadCandidates(String modelName) {
        List<Instance> all = getAvailableInstances();
        if (all.isEmpty()) return List.of();

        List<RoutedVendor> candidates = all.stream()
            .filter(i -> i.getVendor() != null)
            .filter(i -> i.getModelName() != null && i.getModelName().equals(modelName))
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
}
