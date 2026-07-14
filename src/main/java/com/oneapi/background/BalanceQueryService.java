package com.oneapi.background;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oneapi.background.balance.*;
import com.oneapi.model.Vendor;
import com.oneapi.repo.VendorRepo;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供应商余额查询服务。
 * 定时轮询所有启用的供应商余额，缓存在 Caffeine 中。
 * 低可靠性要求：单个供应商失败不影响其他，只 log.warn。
 */
@Slf4j
public class BalanceQueryService {
    private final VendorRepo vendorRepo;
    private final List<BalanceProvider> providers;
    private final Cache<Integer, BalanceInfo> cache;
    private final Map<Integer, BalanceInfo> latestResults = new ConcurrentHashMap<>();

    public BalanceQueryService(VendorRepo vendorRepo) {
        this(vendorRepo, Duration.ofMinutes(5));
    }

    public BalanceQueryService(VendorRepo vendorRepo, Duration cacheTtl) {
        this.vendorRepo = vendorRepo;
        this.providers = new ArrayList<>(List.of(
            new DeepSeekBalanceProvider(),
            new MoonshotBalanceProvider(),
            new SiliconFlowBalanceProvider(),
            new OpenRouterBalanceProvider(),
            new MiniMaxBalanceProvider(),
            new MimoBalanceProvider(),
            new VolcengineBalanceProvider()
        ));
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(cacheTtl)
            .maximumSize(100)
            .build();
    }

    /**
     * 查询所有启用供应商的余额。
     * 单个失败不影响其他。
     */
    public Map<Integer, BalanceInfo> queryAll() {
        List<String> errors = new ArrayList<>();

        for (Vendor vendor : vendorRepo.findAllActive()) {
            BalanceProvider provider = findProvider(vendor);
            if (provider == null) continue; // no provider for this vendor
            try {
                BalanceInfo info = provider.queryBalance(vendor);
                if (info != null) {
                    cache.put(vendor.getId(), info);
                    latestResults.put(vendor.getId(), info);
                } else {
                    log.debug("balance query skipped for vendor {} (no credential)", vendor.getName());
                }
            } catch (Exception ex) {
                errors.add("vendor " + vendor.getName() + ": " + ex.getMessage());
                log.warn("balance query failed for vendor {}: {}", vendor.getName(), ex.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            log.warn("balance query completed with {} errors: {}", errors.size(), errors);
        }
        return Map.copyOf(latestResults);
    }

    /**
     * 获取缓存的余额（按供应商 ID）。
     * 返回 null 表示缓存过期或未查询过。
     */
    public BalanceInfo getBalance(int vendorId) {
        BalanceInfo cached = cache.getIfPresent(vendorId);
        if (cached != null) return cached;
        return latestResults.get(vendorId);
    }

    /**
     * 获取所有缓存的余额。
     */
    public Map<Integer, BalanceInfo> getAllBalances() {
        return Map.copyOf(latestResults);
    }

    private BalanceProvider findProvider(Vendor vendor) {
        for (BalanceProvider provider : providers) {
            if (provider.supports(vendor)) return provider;
        }
        return null;
    }
}
