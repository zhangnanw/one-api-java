package com.oneapi.background;

import com.oneapi.repository.HolographicLogRepository;
import com.oneapi.entity.HolographicLog;
import com.oneapi.model.HolographicRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 全息调试日志写入器。写入失败静默丢弃，不影响主链路。
 * <p>
 * 现在作为 Spring 组件管理，通过 JPA 写入 holographic_logs。
 * 不再直接操作 JDBC。
 */
@Slf4j
@Component
public class HolographicLogger {

    private final HolographicLogRepository holographicLogRepository;

    public HolographicLogger(HolographicLogRepository holographicLogRepository) {
        this.holographicLogRepository = holographicLogRepository;
        log.info("HolographicLogger initialized");
    }

    public void write(HolographicRecord record) {
        if (record == null) return;
        try {
            HolographicLog entity = new HolographicLog();
            entity.setRequestId(record.requestId());
            entity.setTimestampMs(record.timestampMs());
            entity.setRequestedModel(record.requestedModel());
            entity.setFinalStatus(record.finalStatus());
            entity.setFinalHttpCode(record.finalHttpCode());
            entity.setTotalLatencyMs(record.totalLatencyMs());
            entity.setTotalTokens(record.totalTokens());
            entity.setData(record.toJson());

            holographicLogRepository.saveAndFlush(entity);
        } catch (Exception e) {
            log.warn("HolographicLogger write failed: {}", e.getMessage(), e);
        }
    }
}
