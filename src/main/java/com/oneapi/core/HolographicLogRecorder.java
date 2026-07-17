package com.oneapi.core;

import com.oneapi.background.HolographicLogger;
import com.oneapi.model.HolographicRecord;
import io.vertx.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Encapsulates holographic log recording boilerplate.
 * Extracted from RelayCoordinator to reduce duplication between buffered and stream paths.
 * All writes run on the Vert.x worker pool so they do not block the event loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolographicLogRecorder {

    private final HolographicLogger holographicLogger;

    /**
     * Record a successful attempt and finalize the holographic log.
     * Used in both buffered (tryBuffered onSuccess) and stream (relayStream statusCode==200) paths.
     */
    public void logAttemptSuccess(RoutingContext ctx, String vendorName, int instanceId,
                                   String upstreamModel, String upstreamUrl,
                                   int statusCode, long latencyMs, int tokens) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        rec.addAttempt(HolographicRecord.AttemptRecord.success(
            vendorName, instanceId, upstreamModel, upstreamUrl,
            statusCode, latencyMs, 0, 0, tokens));
        long totalMs = System.currentTimeMillis() - rec.startMs();
        rec.finish("success", statusCode, totalMs, tokens,
            null, null, null, rec.attemptCount(),
            vendorName, instanceId);
        writeAsync(ctx, rec);
    }

    /**
     * Record a successful buffered attempt (with promptTokens/completionTokens).
     * For the buffered path where we have split token counts from RelayResult.
     */
    public void logAttemptSuccessBuffered(RoutingContext ctx, String vendorName, int instanceId,
                                           String upstreamModel, String upstreamUrl,
                                           int statusCode, long latencyMs,
                                           int promptTokens, int completionTokens,
                                           String responseBody) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        rec.addAttempt(HolographicRecord.AttemptRecord.success(
            vendorName, instanceId, upstreamModel, upstreamUrl,
            statusCode, latencyMs, 0, promptTokens, completionTokens));
        long totalMs = System.currentTimeMillis() - rec.startMs();
        int totalTokens = promptTokens + completionTokens;
        rec.finish("success", statusCode, totalMs, totalTokens,
            responseBody, null, null, rec.attemptCount(),
            vendorName, instanceId);
        writeAsync(ctx, rec);
    }

    /**
     * Record a failed attempt (does NOT finalize - allows more attempts).
     * Used in both buffered (onFailure) and stream (statusCode!=200 fallback) paths.
     */
    public void logAttemptFailure(RoutingContext ctx, String vendorName, int instanceId,
                                   String upstreamModel, String upstreamUrl,
                                   int statusCode, long latencyMs,
                                   String errorType, String errorMessage, boolean cooled) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        rec.addAttempt(HolographicRecord.AttemptRecord.failure(
            vendorName, instanceId, upstreamModel, upstreamUrl,
            statusCode, latencyMs, errorType, errorMessage, cooled));
    }

    /**
     * Finalize log when all candidates exhausted with failure.
     * Used when queue is empty in tryBuffered, and when all stream candidates fail.
     */
    public void logAllFailed(RoutingContext ctx, int statusCode, String errorMessage) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        long totalMs = System.currentTimeMillis() - rec.startMs();
        rec.finish("failure", statusCode, totalMs, 0, null,
            "relay", errorMessage, rec.attemptCount(), null, 0);
        writeAsync(ctx, rec);
    }

    /**
     * Finalize log when no candidates available at all (before any attempt).
     */
    public void logNoCandidates(RoutingContext ctx, String modelName) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        long totalMs = System.currentTimeMillis() - rec.startMs();
        rec.finish("failure", 503, totalMs, 0, null,
            "relay", "no available instances for " + modelName, 0, null, 0);
        writeAsync(ctx, rec);
    }

    /**
     * Finalize log when stage 2 model resolution fails.
     */
    public void logStage2Error(RoutingContext ctx, int statusCode, String errorMessage) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        long totalMs = System.currentTimeMillis() - rec.startMs();
        rec.finish("failure", statusCode, totalMs, 0, null,
            "stage2", errorMessage, 0, null, 0);
        writeAsync(ctx, rec);
    }

    /**
     * Finalize log when stage 3 instance filtering fails (e.g. BodyLimitFilter).
     */
    public void logStage3Error(RoutingContext ctx, int statusCode, String errorMessage) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        long totalMs = System.currentTimeMillis() - rec.startMs();
        rec.finish("failure", statusCode, totalMs, 0, null,
            "stage3", errorMessage, 0, null, 0);
        writeAsync(ctx, rec);
    }

    /**
     * Finalize log when all candidates are filtered out by stage 3.
     */
    public void logAllFiltered(RoutingContext ctx, String modelName) {
        var rec = ctx.<HolographicRecord>get("holographicRecord");
        if (rec == null) return;
        long totalMs = System.currentTimeMillis() - rec.startMs();
        rec.finish("failure", 400, totalMs, 0, null,
            "stage3", "all instances filtered for " + modelName, 0, null, 0);
        writeAsync(ctx, rec);
    }

    private void writeAsync(RoutingContext ctx, HolographicRecord rec) {
        ctx.vertx().executeBlocking(() -> {
            holographicLogger.write(rec);
            return null;
        }).onFailure(err -> log.error("holographic log write failed: {}", err.getMessage()));
    }
}
