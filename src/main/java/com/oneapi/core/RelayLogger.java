package com.oneapi.core;

import com.oneapi.model.RelayLog;
import com.oneapi.service.RelayLogService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * RelayLogger 兼容外观。
 * <p>
 * 底层写入已改为通过 {@link RelayLogService} 走 JPA 异步写入。
 * 保留此类是为了减少调用方改动；内部不再持有 DataSource 或执行 JDBC。
 */
@Slf4j
public class RelayLogger {

    private static RelayLogService relayLogService;

    public static void init(RelayLogService service) {
        relayLogService = service;
        log.info("RelayLogger initialized (JPA async mode)");
    }

    public static CompletableFuture<Long> insert(RelayLog rlog) {
        if (relayLogService == null) {
            log.debug("RelayLogService not initialized, skip insert");
            return CompletableFuture.completedFuture(-1L);
        }
        return relayLogService.insertAsync(rlog);
    }

    public static CompletableFuture<Void> updateStreamResult(long id, int code, int tokens, long latencyMs, String err) {
        if (relayLogService == null) {
            log.debug("RelayLogService not initialized, skip update");
            return CompletableFuture.completedFuture(null);
        }
        return relayLogService.updateStreamResultAsync(id, code, tokens, latencyMs, err);
    }
}
