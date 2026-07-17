package com.oneapi.repository;

import com.oneapi.entity.HolographicLog;
import com.oneapi.entity.RelayLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 HolographicLogRepository / RelayLogRepository 都能在 H2 PostgreSQL 兼容模式下
 * 正确建表、保存、读取数据。这是 Spring 化迁移后日志通道的核心集成测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class LogRepositoryTest {

    @Autowired
    private HolographicLogRepository holographicRepo;

    @Autowired
    private RelayLogRepository relayRepo;

    @Test
    void holographicLogSaveAndRead() {
        HolographicLog e = new HolographicLog();
        e.setRequestId("test-req-" + System.nanoTime());
        e.setTimestampMs(System.currentTimeMillis());
        e.setRequestedModel("gpt-4");
        e.setFinalStatus("ok");
        e.setFinalHttpCode(200);
        e.setTotalLatencyMs(123L);
        e.setTotalTokens(42);
        e.setData("{\"foo\":\"bar\"}");

        HolographicLog saved = holographicRepo.saveAndFlush(e);
        assertNotNull(saved.getId());

        var found = holographicRepo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertTrue(found.get().getData().contains("foo"));
    }

    @Test
    void relayLogSaveAndRead() {
        RelayLogEntity e = new RelayLogEntity();
        e.setTs(Instant.now());
        e.setInstanceId(1);
        e.setBaseUrl("https://api.openai.com");
        e.setTokenName("test-token");
        e.setUserId(42);
        e.setModelOrig("gpt-4");
        e.setUpstreamModel("gpt-4-turbo");
        e.setStream(1);
        e.setBodySize(100);
        e.setCode(200);
        e.setRespSize(200);
        e.setTokens(50);
        e.setLatencyMs(800L);

        RelayLogEntity saved = relayRepo.saveAndFlush(e);
        assertNotNull(saved.getId());

        var found = relayRepo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertTrue("https://api.openai.com".equals(found.get().getBaseUrl()));
    }
}
