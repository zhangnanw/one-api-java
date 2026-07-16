package com.oneapi.background;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.oneapi.background.balance.*;
import com.oneapi.repository.VendorRepository;
import com.oneapi.model.Vendor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 供应商余额查询服务�? * 定时轮询所有启用的供应商余额，缓存�?Caffeine 中�? * 低可靠性要求：单个供应商失败不影响其他，只 log.warn�? */
@Slf4j
@Service
public class BalanceQueryService {
    private final VendorRepository vendorRepo;
    private final List<BalanceProvider> providers;
    private final Cache<Integer, BalanceInfo> cache;
    private final Map<Integer, BalanceInfo> latestResults = new ConcurrentHashMap<>();

    @Autowired
    public BalanceQueryService(VendorRepository vendorRepo, ObjectMapper objectMapper) {
        this(vendorRepo, objectMapper, Duration.ofMinutes(5));
    }

    public BalanceQueryService(VendorRepository vendorRepo, ObjectMapper objectMapper, Duration cacheTtl) {
        this.vendorRepo = vendorRepo;
        this.providers = new ArrayList<>(List.of(
            new DeepSeekBalanceProvider(objectMapper),
            new MoonshotBalanceProvider(objectMapper),
            new SiliconFlowBalanceProvider(objectMapper),
            new OpenRouterBalanceProvider(objectMapper),
            new MiniMaxBalanceProvider(objectMapper),
            new MimoBalanceProvider(objectMapper),
            new VolcengineBalanceProvider(objectMapper)
        ));
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(cacheTtl)
            .maximumSize(100)
            .build();
    }

    /**
     * 查询所有启用供应商的余额�?     * 单个失败不影响其他�?     */
    public Map<Integer, BalanceInfo> queryAll() {
        List<String> errors = new ArrayList<>();

        for (Vendor vendor : vendorRepo.findByStatus(1)) {
            BalanceProvider provider = findProvider(vendor);
            if (provider == null) {
                log.warn("No balance provider for vendor {}", vendor.getName());
                continue;
            }
            try {
                BalanceInfo info = provider.queryBalance(vendor);
                if (info != null) {
                    cache.put(vendor.getId(), info);
                    latestResults.put(vendor.getId(), info);
                }
            } catch (Exception e) {
                errors.add(vendor.getName() + ": " + e.getMessage());
                log.warn("Balance query failed for vendor {}: {}", vendor.getName(), e.getMessage());
            }
        }
        return Map.copyOf(latestResults);
    }

    public BalanceInfo getBalance(int vendorId) {
        BalanceInfo cached = cache.getIfPresent(vendorId);
        if (cached != null) return cached;

        Vendor vendor = vendorRepo.findById(vendorId).orElse(null);
        if (vendor == null) return null;
        BalanceProvider provider = findProvider(vendor);
        if (provider == null) return null;
        try {
            BalanceInfo info = provider.queryBalance(vendor);
            if (info != null) {
                cache.put(vendorId, info);
                latestResults.put(vendorId, info);
            }
            return info;
        } catch (Exception e) {
            log.warn("Balance query failed for vendor {}: {}", vendor.getName(), e.getMessage());
            return null;
        }
    }

    private BalanceProvider findProvider(Vendor vendor) {
        return providers.stream()
            .filter(p -> p.supports(vendor))
            .findFirst()
            .orElse(null);
    }
}
