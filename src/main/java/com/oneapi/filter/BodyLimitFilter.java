package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
import com.oneapi.repo.ModelCatalogRepo;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stage 3 filter — removes candidates whose context window is smaller than the request body.
 * <p>
 * Heuristic: body bytes &gt; context_window tokens → candidate too small to handle this request.
 * Models with no catalog entry (unknown window) are kept.
 * If all candidates are filtered out, marks the context with a 413 {@link RelayError.BodyTooLarge}.
 */
public class BodyLimitFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(BodyLimitFilter.class);

    private final ModelCatalogRepo catalogRepo;

    public BodyLimitFilter(ModelCatalogRepo catalogRepo) {
        this.catalogRepo = catalogRepo;
    }

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        byte[] rawBody = ctx.rawBody();
        int bodyLen = rawBody == null ? 0 : rawBody.length;
        if (bodyLen == 0) {
            return ctx;
        }

        int before = candidates.size();
        List<RoutedVendor> filtered = candidates.stream()
            .filter(rv -> acceptable(rv, bodyLen))
            .toList();
        int removed = before - filtered.size();

        if (filtered.isEmpty()) {
            String model = candidates.getFirst().modelName();
            String msg = String.format("request body (%d bytes) exceeds context window of all %d models",
                bodyLen, before);
            log.warn("BodyLimitFilter: {} — marking 413 BodyTooLarge", msg);
            ctx.setCandidates(filtered);
            ctx.markError(new RelayError.BodyTooLarge(model, bodyLen, 0), msg);
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
        return bodyLen <= window;
    }
}
