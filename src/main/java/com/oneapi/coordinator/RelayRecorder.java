package com.oneapi.coordinator;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayLog;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import com.oneapi.service.RelayLogger;

/**
 * 即发即忘的中继日志写入器。
 * 所有方法均为非阻塞；失败会被静默丢弃。
 */
public class RelayRecorder {
    /** 启动日志条目，返回日志 ID 供后续更新。 */
    public long start(RelayRequest req, Candidate candidate) {
        RelayLog relayLog = new RelayLog();
        relayLog.setTimestamp(System.currentTimeMillis() / 1000);
        relayLog.setModelOrig(req.requestedModel());
        relayLog.setUpstreamModel(candidate.upstreamModel());
        relayLog.setBaseUrl(candidate.vendor() != null ? candidate.vendor().getBaseUrl() : "");
        if (candidate.instance() != null) {
            relayLog.setInstanceId(candidate.instance().getId());
        }
        relayLog.setStream(req.streaming());
        relayLog.setBodySize(req.rawBody() != null ? req.rawBody().length : 0);
        return RelayLogger.insert(relayLog);
    }

    /** 缓冲式成功 — 更新状态码 + token 数 + 延迟。 */
    public void complete(long logId, RelayResult result, long startMs) {
        int tokens = result.promptTokens() + result.completionTokens();
        long latency = System.currentTimeMillis() - startMs;
        RelayLogger.updateStreamResult(logId, result.httpStatus(), tokens, latency);
    }

    /** 失败 — 更新错误字段。 */
    public void fail(long logId, int code, String err, long latencyMs) {
        RelayLogger.updateStreamResult(logId, code, 0, latencyMs, err);
    }

    /** 流式完成 — 更新最终统计。 */
    public void completeStream(long logId, int code, int tokens, long latencyMs) {
        RelayLogger.updateStreamResult(logId, code, tokens, latencyMs);
    }
}
