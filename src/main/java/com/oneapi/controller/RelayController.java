package com.oneapi.controller;

import com.oneapi.handler.*;
import com.oneapi.model.RelayLog;
import com.oneapi.service.RelayLogger;
import com.oneapi.service.RouterService;
import com.oneapi.service.RouterService.RoutedVendor;
import com.oneapi.service.SessionTracker;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelayController {
    private static final Logger log = LoggerFactory.getLogger(RelayController.class);

    private final RouterService router = new RouterService();
    private final UpstreamClient upstream;
    private final SessionTracker sessions = new SessionTracker();
    private static final int MAX_RETRIES = 2;

    public RelayController(Vertx vertx) {
        this.upstream = new UpstreamClient(WebClient.create(vertx), vertx);
    }

    public void handle(RoutingContext ctx) {
        ctx.request().bodyHandler(buf -> doHandle(ctx, buf.getBytes()));
    }

    private void doHandle(RoutingContext ctx, byte[] rawBody) {
        String bodyStr = new String(rawBody);
        // Parse model from query or body
        String requestedModel = ctx.request().getParam("model");
        if (requestedModel == null) {
            try {
                requestedModel = new JsonObject(bodyStr).getString("model");
            } catch (Exception e) {
                log.debug("body parse failed: {}", e.getMessage());
            }
        }
        if (requestedModel == null || requestedModel.isEmpty()) {
            error(ctx, 400, "model name required");
            return;
        }
        ctx.put("model_name", requestedModel);

        // --- Request ID ---
        String reqId = UUID.randomUUID().toString().substring(0, 8);
        ctx.put("req_id", reqId);

        // --- Session tracking ---
        var messages = SessionTracker.parseMessages(rawBody);
        if (!messages.isEmpty()) {
            String sessionId = sessions.match(messages);
            ctx.put("session_id", sessionId);
            log.debug("session={} msgs={}", sessionId, messages.size());
        }

        // Route
        var result = router.getBestVendor(
            (String) ctx.get("token_hash"), requestedModel,
            router.buildFilters(requestedModel));

        if (!result.isOk()) {
            error(ctx, 503, "model [" + requestedModel + "] no available instances: " + result.error());
            return;
        }

        // Go: reasoning filter — restrict to reasoning-capable instances
        var relayCandidates = result.candidates();
        boolean isReasoningRequest = (requestedModel != null && requestedModel.endsWith("-max"))
            || bodyStr.contains("reasoning:max");
        if (isReasoningRequest) {
            var filtered = new java.util.ArrayList<>(result.candidates());
            filtered.removeIf(c -> !c.instanceTags().contains("capability:reasoning")
                                && !com.oneapi.service.FilterUtils.parseTags(c.vendor().getMeta()).contains("capability:reasoning"));
            if (filtered.isEmpty()) {
                error(ctx, 503, "no reasoning-capable instance available");
                return;
            }
            relayCandidates = filtered;
        }

        // Relay with retry
        tryRelay(ctx, rawBody, relayCandidates, 0, 0);
    }

    private void tryRelay(RoutingContext ctx, byte[] rawBody,
                          java.util.List<RoutedVendor> candidates, int idx, int retry) {

        if (retry > MAX_RETRIES) {
            error(ctx, 503, "model exhausted retries");
            return;
        }

        var cr = router.getNextCandidate(candidates, idx);
        if (!cr.isOk()) {
            error(ctx, 503, "all vendors busy");
            return;
        }

        var rv = cr.candidate();
        var extraHeaders = MultiMap.caseInsensitiveMultiMap();
        String bodyStr = new String(rawBody);

        String modelName = ctx.get("model_name");
        if ((modelName != null && modelName.endsWith("-max"))
            || (rawBody != null && bodyStr.contains("reasoning:max"))) {
            extraHeaders.set("X-Reasoning-Effort", "max");
        }
        if (rv.vendor().getBaseUrl() != null && rv.vendor().getBaseUrl().contains("kimi.com")) {
            extraHeaders.set("User-Agent", "KimiCLI/1.6");
        }

        var relayReq = new UpstreamClient.RelayRequest(
            rv.vendor().getBaseUrl(), rv.vendor().getApiKey(),
            ctx.request().path(), ctx.request().method().name(), rawBody, extraHeaders);

        final byte[] finalBody = substituteModel(rawBody, rv.upstreamModel(), rv.modelName());

        var finalReq = new UpstreamClient.RelayRequest(
            relayReq.baseUrl(), relayReq.apiKey(),
            relayReq.requestPath(), relayReq.method(), finalBody, relayReq.extraHeaders());

        String reqId = ctx.get("req_id");
        String sessionId = ctx.get("session_id");
        log.info("[req={}] relay -> {} #{} model={} session={}", reqId, rv.vendor().getName(), rv.instanceId(), rv.modelName(), sessionId);

        // --- RelayLog ---
        RelayLog rlog = new RelayLog();
        rlog.ts = System.currentTimeMillis() / 1000;
        rlog.instanceId = rv.instanceId();
        rlog.baseUrl = rv.vendor().getBaseUrl();
        rlog.tokenName = ctx.get("token_hash") != null ? (String) ctx.get("token_hash") : "";
        rlog.userId = ctx.get("user_id") != null ? (int) ctx.get("user_id") : 0;
        rlog.modelOrig = modelName;
        rlog.modelReal = rv.upstreamModel() != null && !rv.upstreamModel().isEmpty()
            ? rv.upstreamModel() : rv.modelName();
        rlog.stream = detectStream(finalBody);
        rlog.bodySize = finalBody.length;
        long startMs = System.currentTimeMillis();

        // --- Streaming path: real pipe, no retry ---
        if (rlog.stream) {
            rlog.code = 0;
            rlog.err = "streaming";
            rlog.latencyMs = System.currentTimeMillis() - startMs;
            long logId = RelayLogger.insert(rlog);

            upstream.relayStream(new UpstreamClient.RelayRequest(
                relayReq.baseUrl(), relayReq.apiKey(),
                relayReq.requestPath(), relayReq.method(), finalBody,
                relayReq.extraHeaders(), ctx.response()),
                (statusCode, tokens) -> {
                    long latency = System.currentTimeMillis() - startMs;
                    if (statusCode < 500) {
                        router.clearCooldown(rv.instanceId(), rv.vendor().getId());
                    } else {
                        router.instanceCooldown(rv.instanceId(), rv.instanceTags());
                    }
                    if (logId > 0) {
                        RelayLogger.updateStreamResult(logId, statusCode, tokens, latency);
                    }
                });
            return;
        }

        upstream.relay(finalReq)
            .compose(resp -> {
                int status = resp.statusCode();
                log.info("[req={}] relay ← {} status={}", reqId, rv.vendor().getName(), status);

                if (status >= 500) {
                    router.vendorCooldown(rv.vendor().getId());
                    rlog.code = status;
                    rlog.respSize = resp.body() != null ? resp.body().length() : 0;
                    rlog.latencyMs = System.currentTimeMillis() - startMs;
                    rlog.err = "upstream " + status;
                    RelayLogger.insert(rlog);
                    tryRelay(ctx, finalBody, candidates, cr.nextIdx(), retry + 1);
                    return Future.succeededFuture();
                }
                if (status >= 400 && status != 429) {
                    router.instanceCooldown(rv.instanceId(), rv.instanceTags());
                    if (status == 401 || status == 403) {
                        router.vendorCooldown(rv.vendor().getId());
                    }
                    rlog.code = status;
                    rlog.respSize = resp.body() != null ? resp.body().length() : 0;
                    rlog.latencyMs = System.currentTimeMillis() - startMs;
                    rlog.err = "upstream " + status;
                    RelayLogger.insert(rlog);
                    tryRelay(ctx, finalBody, candidates, cr.nextIdx(), retry + 1);
                    return Future.succeededFuture();
                }

                // Success
                router.clearCooldown(rv.instanceId(), rv.vendor().getId());
                rlog.code = status;
                rlog.respSize = resp.body() != null ? resp.body().length() : 0;

                // parse tokens from response body (align Go parseTokens)
                if (resp.body() != null) {
                    try {
                        var bj = new JsonObject(resp.body().toString());
                        var usage = bj.getJsonObject("usage");
                        if (usage != null) {
                            rlog.tokens = usage.getInteger("total_tokens", 0);
                        }
                    } catch (Exception e) {
                        // silent — Go also silently returns 0 on parse failure
                    }
                }

                rlog.latencyMs = System.currentTimeMillis() - startMs;
                RelayLogger.insert(rlog);

                ctx.response().setStatusCode(status);
                resp.headers().forEach(e -> ctx.response().putHeader(e.getKey(), e.getValue()));
                Buffer b = resp.body();
                ctx.response().end(b != null ? b : Buffer.buffer());
                return Future.succeededFuture();
            })
            .onFailure(err -> {
                log.error("relay failed: {}", err.getMessage());
                router.vendorCooldown(rv.vendor().getId());
                rlog.code = 0;
                rlog.latencyMs = System.currentTimeMillis() - startMs;
                rlog.err = err.getMessage();
                RelayLogger.insert(rlog);
                tryRelay(ctx, finalBody, candidates, cr.nextIdx(), retry + 1);
            });
    }

    private byte[] substituteModel(byte[] body, String upstreamModel, String instanceModel) {
        if (upstreamModel == null || upstreamModel.isEmpty()) {
            upstreamModel = instanceModel;
        }
        try {
            var bj = new JsonObject(new String(body));
            String currentModel = bj.getString("model");
            if (!upstreamModel.equals(currentModel)) {
                bj.put("model", upstreamModel);
                return bj.toString().getBytes();
            }
        } catch (Exception ignore) {}
        return body;
    }

    private void error(RoutingContext ctx, int status, String msg) {
        ctx.response().setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error",
                new JsonObject().put("message", msg).put("type", "one_api_error")).toString());
    }

    private static boolean detectStream(byte[] body) {
        if (body == null) return false;
        String s = new String(body);
        return s.contains("\"stream\":true") || s.contains("\"stream\": true");
    }
}
