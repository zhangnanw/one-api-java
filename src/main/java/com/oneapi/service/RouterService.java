package com.oneapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneapi.model.Candidate;
import com.oneapi.model.Instance;
import com.oneapi.model.Vendor;
import com.oneapi.repo.InstanceRepo;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RouterService {
    private final InstanceRepo instanceRepo;

    public RouterService() {
        this.instanceRepo = new InstanceRepo();
    }

    /** For testing — inject custom DataSource. */
    public RouterService(DataSource ds) {
        this.instanceRepo = new InstanceRepo(ds);
    }

    // 10 秒 TTL 缓存（与 Go 版的 cacheTTL 一致）
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
     * 为给定模型加载原始候选实例 — 不过滤、不排序。
     * 由 V2 协调器使用，后者运行自己的过滤器链。
     * 返回所有 model_name 匹配所请求模型名称的实例。
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

    // --- 缓存 ---

    private List<Instance> getCachedInstances() {
        return instanceCache.get("all", k -> instanceRepo.findAllWithVendor());
    }
}
