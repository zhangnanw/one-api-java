package com.oneapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.Arrays;

/**
 * In-memory cooldown matching Go's sync.Map + damping decay.
 * Restart clears all cooldowns.
 */
@Slf4j
public class CooldownService {

    // Cooldown entries — Caffeine auto-expires after cap
    private final Cache<Integer, CooldownEntry> instanceCooldowns = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(100, TimeUnit.MINUTES)  // max 100 min
        .recordStats()
        .build();
    private final Cache<Integer, CooldownEntry> vendorCooldowns = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(100, TimeUnit.MINUTES)
        .recordStats()
        .build();

    /**
     * Damping decay: minutes = 5 * (1 - 0.95^n) / 0.05
     */
    public static long calcDuration(int n) {
        if (n <= 0) n = 1;
        double minutes = 5.0 * (1.0 - Math.pow(0.95, n)) / 0.05;
        long seconds = (long)(minutes * 60);
        return seconds;
    }

    public boolean hasNoCoolTag(String tags) {
        return tags != null && Arrays.asList(tags.split(",")).contains("nocool:true");
    }

    public boolean isInstanceInCooldown(int instanceId, String tags) {
        if (hasNoCoolTag(tags)) return false;
        var entry = instanceCooldowns.getIfPresent(instanceId);
        return entry != null && System.currentTimeMillis() / 1000 < entry.until;
    }

    /**
     * 原子检查并设置实例冷却。
     * 返回 true 表示已经在冷却中（调用方应跳过该实例），false 表示新设置了冷却（调用方应视为本次失败已处理）。
     * 使用 compute() 保证并发安全：多个线程同时失败时，只有第一个会设置冷却，后续的会看到 old.until > now 而返回 true。
     *
     * <p>关键语义：实例已在冷却时保持 n 不变（不会再 +1）——避免反复失败把冷却窗口无限延长。
     */
    public boolean checkAndSetInstanceCooldown(int instanceId, String tags) {
        if (hasNoCoolTag(tags)) return false;
        long now = System.currentTimeMillis() / 1000;
        CooldownEntry[] oldRef = new CooldownEntry[1];
        instanceCooldowns.asMap().compute(instanceId, (k, old) -> {
            oldRef[0] = old;
            if (old != null && now < old.until) {
                return old; // still cooling, keep it
            }
            int n = (old != null) ? old.n + 1 : 1;
            long until = now + calcDuration(n);
            log.debug("cooldown id={} n={} until={}", k, n, until);
            return new CooldownEntry(until, n);
        });
        return oldRef[0] != null && now < oldRef[0].until;
    }

    public boolean isVendorInCooldown(int vendorId) {
        var entry = vendorCooldowns.getIfPresent(vendorId);
        return entry != null && System.currentTimeMillis() / 1000 < entry.until;
    }

    /**
     * 原子检查并设置供应商冷却。
     * 与 {@link #checkAndSetInstanceCooldown} 逻辑相同，但作用于供应商级别。
     */
    public boolean checkAndSetVendorCooldown(int vendorId) {
        long now = System.currentTimeMillis() / 1000;
        CooldownEntry[] oldRef = new CooldownEntry[1];
        vendorCooldowns.asMap().compute(vendorId, (k, old) -> {
            oldRef[0] = old;
            if (old != null && now < old.until) {
                return old; // still cooling, keep it
            }
            int n = (old != null) ? old.n + 1 : 1;
            long until = now + calcDuration(n);
            log.debug("cooldown vendorId={} n={} until={}", k, n, until);
            return new CooldownEntry(until, n);
        });
        return oldRef[0] != null && now < oldRef[0].until;
    }

    public long getCooldownRemaining(int instanceId) {
        var entry = instanceCooldowns.getIfPresent(instanceId);
        if (entry == null) return 0;
        return Math.max(0, entry.until - System.currentTimeMillis() / 1000);
    }

    /**
     * 无条件 +1 冷却 n：不管旧 n 是不是在冷却中，本调用都会让 n 自增并把 until 推到对应位置。
     *
     * <p>与 {@link #checkAndSetInstanceCooldown} 的差异：
     * <ul>
     *   <li>{@code checkAndSet} 在已冷却时不增加 n（避免窗口过长）；返回 {@code boolean} 供调用方判断</li>
     *   <li>{@code set} 永远 +1（用于响应"200 但空响应体"或"明确错误信号"这类强制冷却场景）</li>
     * </ul>
     *
     * <p>目前调用点：{@code RelayCoordinator.tryBuffered} 200-empty 路径与 relayStream fallback 路径；
     * 是有意选择更严厉的语义。
     */
    public void setInstanceCooldown(int instanceId, String tags) {
        if (hasNoCoolTag(tags)) return;
        applyCooldown(instanceCooldowns, instanceId);
    }

    public void setVendorCooldown(int vendorId) {
        applyCooldown(vendorCooldowns, vendorId);
    }

    public void clearCooldown(int instanceId, int vendorId) {
        if (instanceId > 0) instanceCooldowns.invalidate(instanceId);
        if (vendorId > 0) vendorCooldowns.invalidate(vendorId);
    }

    private void applyCooldown(Cache<Integer, CooldownEntry> cache, int id) {
        cache.asMap().compute(id, (k, old) -> {
            int n = (old != null) ? old.n + 1 : 1;
            long until = System.currentTimeMillis() / 1000 + calcDuration(n);
            log.debug("cooldown id={} n={} until={}", k, n, until);
            return new CooldownEntry(until, n);
        });
    }

    /**
     * 两个 cooldown 缓存的合并 stats。用于 /api/status 健康检查段。
     * 字段：hitCount / missCount / evictionCount / estimatedSize（两者相加）。
     */
    public CooldownStats getStats() {
        CacheStats inst = instanceCooldowns.stats();
        CacheStats vend = vendorCooldowns.stats();
        return new CooldownStats(
            inst.hitCount() + vend.hitCount(),
            inst.missCount() + vend.missCount(),
            inst.evictionCount() + vend.evictionCount(),
            instanceCooldowns.estimatedSize() + vendorCooldowns.estimatedSize()
        );
    }

    /** Cooldown 缓存合并指标。 */
    public record CooldownStats(
        long hitCount,
        long missCount,
        long evictionCount,
        long estimatedSize
    ) {}

    // --- inner class ---

    public record CooldownEntry(long until, int n) {}
}
