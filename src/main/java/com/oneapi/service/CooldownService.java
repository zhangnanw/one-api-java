package com.oneapi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * In-memory cooldown matching Go's sync.Map + damping decay.
 * Restart clears all cooldowns.
 */
public class CooldownService {
    private static final Logger log = LoggerFactory.getLogger(CooldownService.class);

    // Cooldown entries — Caffeine auto-expires after cap
    private final Cache<Integer, CooldownEntry> instanceCooldowns = Caffeine.newBuilder()
        .expireAfterAccess(100, TimeUnit.MINUTES)  // max 100 min
        .build();
    private final Cache<Integer, CooldownEntry> vendorCooldowns = Caffeine.newBuilder()
        .expireAfterAccess(100, TimeUnit.MINUTES)
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
        return tags != null && tags.contains("nocool:true");
    }

    public boolean isInstanceInCooldown(int instanceId, String tags) {
        if (hasNoCoolTag(tags)) return false;
        var entry = instanceCooldowns.getIfPresent(instanceId);
        return entry != null && System.currentTimeMillis() / 1000 < entry.until;
    }

    /**
     * Atomically check and set instance cooldown.
     * Returns true if already cooling (caller should skip), false if newly set.
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
     * Atomically check and set vendor cooldown.
     * Returns true if already cooling (caller should skip), false if newly set.
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

    // --- inner class ---

    public record CooldownEntry(long until, int n) {}
}
