package com.oneapi.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneapi.model.Candidate;
import com.oneapi.model.Instance;
import com.oneapi.model.Vendor;
import com.oneapi.repo.InstanceRepo;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class RouterService {
    private final InstanceRepo instanceRepo;
    private CooldownService cooldownService; // 可选，由协调器注入

    public RouterService(InstanceRepo instanceRepo) {
        this.instanceRepo = instanceRepo;
    }

    /**
     * 注入 CooldownService（由 RelayCoordinator 在构造时调用）。
     * 用于在 loadCandidates 层预过滤冷却中的实例/供应商，
     * 避免它们参与后续的排序，节省无效计算。
     */
    public void setCooldownService(CooldownService cooldownService) {
        this.cooldownService = cooldownService;
    }

    // 60 秒 TTL 缓存（避免频繁查库）
    private final Cache<String, List<Instance>> instanceCache = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS).build();

    // --- RoutedVendor ---

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
        /**
         * 从路由后中间表示转换为中继候选。
         * 重建 Instance 壳（包含 modelName/upstreamModel/vendor/status/meta），
         * 供 RelayCoordinator 选路使用。
         */
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
     * 为给定模型加载候选实例。
     *
     * 过滤层级：
     * 1. 基础状态过滤 — 排除 DISABLED / DEPRECATED / FAILED / UNKNOWN
     * 2. 冷却预过滤（若 cooldownService 已注入）— 在排序前排除冷却中的实例/供应商
     *
     * 注意：冷却中的实例不会参与排序，但 CooldownFilter（stage-3）仍保留
     * 作为安全网，捕获 loadCandidates 之后、新冷却的实例。
     */
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
                String.join(",", FilterUtils.parseTags(i.getMeta())),
                i.getMeta(),
                i.getStatus(),
                i.getPref(),
                i.getLayer()
            ))
            .toList();

        // 冷却预过滤：避免冷却实例参与后续排序
        if (cooldownService != null) {
            candidates = candidates.stream()
                .filter(rv -> {
                    boolean instCool = cooldownService.isInstanceInCooldown(rv.instanceId(), rv.instanceTags());
                    boolean vendCool = rv.vendor() != null
                        && cooldownService.isVendorInCooldown(rv.vendor().getId());
                    return !instCool && !vendCool;
                })
                .toList();
        }
        return candidates;
    }

    // --- 缓存 ---

    private List<Instance> getCachedInstances() {
        return instanceCache.get("all", k -> instanceRepo.findAllWithVendor());
    }
}
