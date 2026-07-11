package com.oneapi.service;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 周期清理超过 retention 阈值的 holographic_logs 记录。
 *
 * <p>由 {@link com.oneapi.config.RouterConfig} 在 build 时启动，
 * 由 {@link RouterConfig#close()} 关停（在 main shutdown hook 中调用）。
 */
@Slf4j
public class HolographicRetentionService implements Closeable {

    private final DataSource dataSource;
    private final int retentionDays;
    private final ScheduledExecutorService scheduler;

    public HolographicRetentionService(DataSource dataSource, int retentionDays) {
        this.dataSource = dataSource;
        this.retentionDays = retentionDays;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "holographic-retention");
            t.setDaemon(true);
            return t;
        });
        long periodHours = Math.max(1L, 24L);  // 每天一次
        scheduler.scheduleAtFixedRate(this::runOnce, 1, periodHours, TimeUnit.HOURS);
        log.info("HolographicRetentionService started: retentionDays={}", retentionDays);
    }

    /** 测试 hook — 立即跑一次清理，与调度器解耦。 */
    public int runOnce() {
        if (retentionDays <= 0 || dataSource == null) {
            return 0;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM holographic_logs WHERE timestamp_ms < ?")) {
            long thresholdMs = System.currentTimeMillis() - ((long) retentionDays) * 24L * 3600L * 1000L;
            ps.setLong(1, thresholdMs);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                log.info("holographic retention deleted {} records (older than {} days)", deleted, retentionDays);
            }
            return deleted;
        } catch (SQLException | NullPointerException e) {
            log.warn("holographic retention failed: {}", e.getMessage());
            return -1;
        }
    }

    /** 测试可注入真实 executor。 */
    public HolographicRetentionService(DataSource dataSource, int retentionDays, ScheduledExecutorService scheduler) {
        this.dataSource = dataSource;
        this.retentionDays = retentionDays;
        this.scheduler = scheduler;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("holographic retention scheduler did not terminate cleanly");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public int retentionDays() { return retentionDays; }
}
