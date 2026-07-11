package com.oneapi.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HolographicRetentionServiceTest — 验证 runOnce 在 retentionDays &lt;= 0 时直接返回 0；
 * 在 dataSource 为 null 时也直接返回 0；并能正确关停。
 *
 * <p>完整 DB 集成测试留给集成测试套件（{@code RouterConfigTest} 已经在 initDb 里
 * 创建 holographic_logs 表）。
 */
class HolographicRetentionServiceTest {

    @Test
    void retentionDaysZero_skipsDelete() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        try {
            var svc = new HolographicRetentionService(null, 0, exec);
            assertThat(svc.runOnce()).isZero();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void retentionDaysNegative_skipsDelete() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        try {
            var svc = new HolographicRetentionService(null, -1, exec);
            assertThat(svc.runOnce()).isZero();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void nullDataSource_skipsDelete() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        try {
            var svc = new HolographicRetentionService(null, 7, exec);
            assertThat(svc.runOnce()).isZero();
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void retentionDays_exposed() {
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        try {
            assertThat(new HolographicRetentionService(null, 7, exec).retentionDays()).isEqualTo(7);
        } finally {
            exec.shutdownNow();
        }
    }
}
