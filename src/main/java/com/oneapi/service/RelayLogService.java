package com.oneapi.service;

import com.oneapi.repository.RelayLogRepository;
import com.oneapi.model.RelayLog;
import com.oneapi.model.RelayLogEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Relay 日志异步写入服务�? * <p>
 * 真实的数据库写入发生�?Spring 管理�?log-worker 线程池中�? * 调用方只需要等�?CompletableFuture，不阻塞 Vert.x eventloop�? */
@Slf4j
@Service
public class RelayLogService {

    private final RelayLogRepository relayLogRepository;

    public RelayLogService(RelayLogRepository relayLogRepository) {
        this.relayLogRepository = relayLogRepository;
    }

    /**
     * 插入 relay 日志并返回自�?ID�?     * 方法本身�?worker 线程中同步执�?JPA save，但调用方在异步 future 中等待结果�?     */
    @Async
    @Transactional
    public CompletableFuture<Long> insertAsync(RelayLog rlog) {
        try {
            RelayLogEntity entity = new RelayLogEntity();
            entity.setTs(Instant.ofEpochSecond(rlog.getTimestamp()));
            entity.setInstanceId(rlog.getInstanceId());
            entity.setBaseUrl(rlog.getBaseUrl());
            entity.setTokenName(rlog.getTokenName());
            entity.setUserId(rlog.getUserId());
            entity.setModelOrig(rlog.getModelOrig());
            entity.setUpstreamModel(rlog.getUpstreamModel());
            entity.setStream(rlog.isStream() ? 1 : 0);
            entity.setBodySize(rlog.getBodySize());
            entity.setCode(rlog.getHttpStatus());
            entity.setRespSize(rlog.getRespSize());
            entity.setTokens(rlog.getTokens());
            entity.setLatencyMs(rlog.getLatencyMs());
            entity.setErrorMessage(rlog.getErrorMessage());

            RelayLogEntity saved = relayLogRepository.saveAndFlush(entity);
            return CompletableFuture.completedFuture(saved.getId());
        } catch (Exception e) {
            log.debug("relay log insert failed: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(-1L);
        }
    }

    @Async
    @Transactional
    public CompletableFuture<Void> updateStreamResultAsync(long id, int code, int tokens, long latencyMs, String err) {
        try {
            relayLogRepository.findById(id).ifPresent(entity -> {
                entity.setCode(code);
                entity.setTokens(tokens);
                entity.setLatencyMs(latencyMs);
                entity.setErrorMessage(err);
                relayLogRepository.save(entity);
            });
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.debug("relay log update failed: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
}
