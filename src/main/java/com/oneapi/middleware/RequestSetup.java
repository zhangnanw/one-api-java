package com.oneapi.middleware;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * RequestSetup — preprocesses relay requests.
 * Extracts model name from body, injects user context and token hash.
 * Matches Go's middleware/request_setup.go.
 *
 * Single-user deployment: userId hardcoded to 1.
 * TokenHash = SHA256(Authorization header) for routing affinity.
 */
public class RequestSetup implements Handler<RoutingContext> {

    private static final int USER_ID = 1; // single-user deployment

    @Override
    public void handle(RoutingContext ctx) {
        // Token hash (used by router for affinity / stats)
        String auth = ctx.request().getHeader("Authorization");
        String tokenHash = sha256(auth != null ? auth : "");
        ctx.put("token_hash", tokenHash);
        ctx.put("user_id", USER_ID);

        ctx.next();
    }

    private static String sha256(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            var sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return input; // fallback
        }
    }
}
