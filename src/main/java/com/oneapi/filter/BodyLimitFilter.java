package com.oneapi.filter;

import com.oneapi.model.RelayContext;

import java.util.ArrayList;
import com.oneapi.model.RelayError;
import com.oneapi.repo.WindowCatalog;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 filter — removes candidates whose context window is smaller than the request body.
 * <p>
 * Heuristic: 1 token ≈ 4 bytes (conservative, matches GPT tokenizer ratio for English).
 * body_bytes &gt; context_window_tokens × 4 → candidate too small.
 * Models with no catalog entry (unknown window) are kept.
 * If all candidates are filtered out, marks the context with a 413 {@link RelayError.BodyTooLarge}.
 */
public class BodyLimitFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(BodyLimitFilter.class);

    /** Bytes-per-token estimate (GPT tokenizer ~4 bytes/token, covers Chinese JSON overhead). */
    static final int BYTES_PER_TOKEN = 4;

    private final WindowCatalog catalogRepo;

    public BodyLimitFilter(WindowCatalog catalogRepo) {
        this.catalogRepo = catalogRepo;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        // Skip for vision requests — image bytes ≠ text tokens.
        // Vision models handle image sizing natively via their own encoders.
        if ("vision".equals(ctx.capabilityRequired())) {
            return ctx;
        }

        byte[] rawBody = ctx.rawBody();
        int bodyLen = rawBody == null ? 0 : rawBody.length;
        if (bodyLen == 0) {
            return ctx;
        }

        int before = candidates.size();
        List<Integer> removedIds = new ArrayList<>();
        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                if (!acceptable(rv, bodyLen)) {
                    removedIds.add(rv.instanceId());
                    return false;
                }
                return true;
            })
            .toList();
        int removed = before - filtered.size();

        if (!removedIds.isEmpty()) {
            int minWindow = candidates.stream()
                .mapToInt(rv -> catalogRepo.getContextWindow(rv.modelName()))
                .filter(w -> w > 0).min().orElse(0);
            int byteLimit = minWindow > 0 ? minWindow * BYTES_PER_TOKEN : 0;
            String reason = String.format("body=%d bytes > window=%d tokens x %d = %d bytes",
                bodyLen, minWindow, BYTES_PER_TOKEN, byteLimit);
            ctx.addFilterAction("BodyLimitFilter", before, filtered.size(), removedIds, reason);
        }

        if (filtered.isEmpty()) {
            // Find the smallest known context window among candidates for the error message
            int minWindow = candidates.stream()
                .mapToInt(rv -> catalogRepo.getContextWindow(rv.modelName()))
                .filter(w -> w > 0)
                .min()
                .orElse(0);
            int byteLimit = minWindow > 0 ? minWindow * BYTES_PER_TOKEN : 0;
            String msg = String.format(
                "request body (%d bytes) exceeds context window (%d tokens ≈ %d bytes)",
                bodyLen, minWindow, byteLimit);
            log.warn("BodyLimitFilter: {} of {} models — marking 413", msg, before);
            ctx.setCandidates(filtered);
            ctx.markError(new RelayError.BodyTooLarge(
                candidates.getFirst().modelName(), bodyLen, minWindow), msg);
            return ctx;
        }

        if (removed > 0) {
            log.debug("BodyLimitFilter: removed {} of {} candidates (body={} bytes)",
                removed, before, bodyLen);
        }
        ctx.setCandidates(filtered);
        return ctx;
    }

    private boolean acceptable(RoutedVendor rv, int bodyLen) {
        int window = catalogRepo.getContextWindow(rv.modelName());
        if (window <= 0) {
            // unknown model, keep
            return true;
        }
        return bodyLen <= window * BYTES_PER_TOKEN;
    }
}
