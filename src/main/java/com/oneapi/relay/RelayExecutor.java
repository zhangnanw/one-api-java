package com.oneapi.relay;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;

/**
 * Execute a relay request against a candidate.
 * Decorator pattern — each implementation wraps or extends another.
 */
public interface RelayExecutor {
    Future<RelayResult> execute(Candidate c, RelayRequest req);
}
