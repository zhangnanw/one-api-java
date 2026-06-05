package com.oneapi.filter;

import com.oneapi.model.RelayContext;

/**
 * Single-responsibility filter for instance/model selection pipeline.
 */
@FunctionalInterface
public interface Filter {
    RelayContext apply(RelayContext ctx);
    // Returns ctx unchanged if pass; sets ctx.error if fail.
}
