package com.oneapi.relay;

import com.oneapi.service.CooldownService;
import com.oneapi.service.FilterUtils;
import com.oneapi.model.Candidate;
import com.oneapi.model.RelayError;
import com.oneapi.model.RelayException;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries on 429 / 5xx failures. Calls CooldownService on 429.
 * Does NOT retry 4xx (except 429).
 * Adds backoff delay before 429 retry (1s, 2s, ...).
 */
public class RetryDecorator implements RelayExecutor {
    private static final Logger log = LoggerFactory.getLogger(RetryDecorator.class);

    private final RelayExecutor inner;
    private final int maxRetries;
    private final CooldownService cooldown;
    private final Vertx vertx;

    public RetryDecorator(RelayExecutor inner, int maxRetries,
                          CooldownService cooldown, Vertx vertx) {
        this.inner = inner;
        this.maxRetries = maxRetries;
        this.cooldown = cooldown;
        this.vertx = vertx;
    }

    @Override
    public Future<RelayResult> execute(Candidate c, RelayRequest req) {
        return executeWithRetry(c, req, 0);
    }

    private Future<RelayResult> executeWithRetry(Candidate c, RelayRequest req, int attempt) {
        return inner.execute(c, req)
            .recover(err -> {
                if (attempt >= maxRetries) {
                    log.warn("retry exhausted after {} attempts: {}", attempt,
                        err.getMessage());
                    return Future.failedFuture(err);
                }
                if (err instanceof RelayException re
                        && re.getError() instanceof RelayError.UpstreamFailure f) {
                    int status = f.httpCode();
                    if (status == 429) {
                        log.warn("429 from vendor={}, cooldown + backoff + retry ({}/{})",
                            c.vendor().getName(), attempt + 1, maxRetries);
                        cooldown.setVendorCooldown(c.vendor().getId());
                        String tags = String.join(",",
                            FilterUtils.parseTags(c.instance().getMeta()));
                        cooldown.setInstanceCooldown(c.instance().getId(), tags);
                        long delayMs = 1000L * (attempt + 1); // 1s, 2s, 3s, ...
                        Promise<RelayResult> retryPromise = Promise.promise();
                        vertx.setTimer(delayMs, tid -> {
                            executeWithRetry(c, req, attempt + 1)
                                .onComplete(retryPromise);
                        });
                        return retryPromise.future();
                    }
                    if (status >= 500) {
                        log.warn("5xx from vendor={}, retry ({}/{})",
                            c.vendor().getName(), attempt + 1, maxRetries);
                        return executeWithRetry(c, req, attempt + 1);
                    }
                }
                // 4xx (non-429) or non-UpstreamFailure → no retry
                return Future.failedFuture(err);
            });
    }
}
