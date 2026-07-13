package com.oneapi.filter;

import com.oneapi.model.RelayContext;

import java.util.ArrayList;
import com.oneapi.model.RelayError;
import com.oneapi.repo.WindowCatalog;
import com.oneapi.core.RouterService.RoutedVendor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Stage 3 filter — removes candidates whose context window is smaller than the request body.
 * <p>
 * 语义（与 {@code REQUIREMENTS.md §F.3.3} 对齐）：
 * <ul>
 *   <li>启发式：1 token ≈ 1 byte（保守估计，避免误放；实际 token 数通常小于字节数）</li>
 *   <li>判定：{@code body_bytes > context_window_tokens} → candidate too small</li>
 *   <li>画像中无记录的模型保持（未知窗口 = 不限）</li>
 *   <li>所有候选被筛 → 标记 {@link RelayError.BodyTooLarge}（413）</li>
 * </ul>
 */
@Slf4j
public class BodyLimitFilter implements Filter {

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
        int[] minWindow = {Integer.MAX_VALUE};
        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> {
                int window = catalogRepo.getContextWindow(rv.modelName());
                if (window > 0 && window < minWindow[0]) {
                    minWindow[0] = window;
                }
                if (!acceptable(rv, bodyLen, window)) {
                    removedIds.add(rv.instanceId());
                    return false;
                }
                return true;
            })
            .toList();
        int removed = before - filtered.size();
        int finalMinWindow = minWindow[0] == Integer.MAX_VALUE ? 0 : minWindow[0];

        if (!removedIds.isEmpty()) {
            String reason = String.format("body=%d bytes > window=%d tokens (1 token ≈ 1 byte)",
                bodyLen, finalMinWindow);
            ctx.addFilterAction("BodyLimitFilter", before, filtered.size(), removedIds, reason);
        }

        if (filtered.isEmpty()) {
            String msg = finalMinWindow > 0
                ? String.format("request body (%d bytes) exceeds smallest known context window (%d tokens)",
                    bodyLen, finalMinWindow)
                : String.format("request body (%d bytes) exceeds context window (catalog unavailable)",
                    bodyLen);
            log.warn("BodyLimitFilter: {} of {} models — marking 413", msg, before);
            ctx.setCandidates(filtered);
            ctx.markError(new RelayError.BodyTooLarge(
                candidates.get(0).modelName(), bodyLen, finalMinWindow), msg);
            return ctx;
        }

        if (removed > 0) {
            log.debug("BodyLimitFilter: removed {} of {} candidates (body={} bytes)",
                removed, before, bodyLen);
        }
        ctx.setCandidates(filtered);
        return ctx;
    }

    /**
     * 是否通过：body 字节数 &lt;= 上下文 token 数（1 token ≈ 1 byte，保守估计）。
     * catalog 中无记录的模型（{@code window <= 0}）视为窗口未知，保留。
     */
    private boolean acceptable(RoutedVendor rv, int bodyLen, int window) {
        if (window <= 0) {
            // unknown model, keep
            return true;
        }
        return bodyLen <= window;
    }
}