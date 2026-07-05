package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Stage 2 filter — detects image content in request body and marks capability requirement.
 * <p>
 * When the request body contains "image_url" in messages, sets
 * {@code capabilityRequired = "vision"} on the context.
 * Actual instance filtering by catalog capabilities happens in stage 3
 * via {@link CapabilityInstanceFilter}.
 * <p>
 * Gracefully handles malformed JSON — returns context unchanged (no vision marking).
 */
public class VisionFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(VisionFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        byte[] rawBody = ctx.rawBody();
        if (rawBody == null || rawBody.length == 0) {
            return ctx;
        }

        String body = new String(rawBody, StandardCharsets.UTF_8);
        if (body.contains("\"image_url\"") || body.contains("\"type\":\"image_url\"")) {
            ctx.setCapabilityRequired("vision");
            log.debug("VisionFilter: detected image_url, setting capabilityRequired=vision");
        }
        return ctx;
    }
}
