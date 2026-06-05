package com.oneapi.coordinator;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayLog;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import com.oneapi.service.RelayLogger;

/**
 * Fire-and-forget relay-log.db writer.
 * All methods are non-blocking; failures are silently dropped.
 */
public class RelayRecorder {
    /** Start log entry, returns log ID for later updates. */
    public long start(RelayRequest req, Candidate c) {
        RelayLog rlog = new RelayLog();
        rlog.ts = System.currentTimeMillis() / 1000;
        rlog.modelOrig = req.requestedModel();
        rlog.modelReal = c.upstreamModel();
        rlog.baseUrl = c.vendor() != null ? c.vendor().getBaseUrl() : "";
        if (c.instance() != null) {
            rlog.instanceId = c.instance().getId();
        }
        rlog.stream = req.isStreaming();
        rlog.bodySize = req.rawBody() != null ? req.rawBody().length : 0;
        return RelayLogger.insert(rlog);
    }

    /** Buffered success — update code + tokens + latency. */
    public void complete(long logId, RelayResult result, long startMs) {
        int tokens = result.promptTokens() + result.completionTokens();
        long latency = System.currentTimeMillis() - startMs;
        RelayLogger.updateStreamResult(logId, result.httpStatus(), tokens, latency);
    }

    /** Failure — update error fields. */
    public void fail(long logId, int code, String err, long latencyMs) {
        RelayLogger.updateStreamResult(logId, code, 0, latencyMs);
    }

    /** Streaming complete — update final stats. */
    public void completeStream(long logId, int code, int tokens, long latencyMs) {
        RelayLogger.updateStreamResult(logId, code, tokens, latencyMs);
    }
}
