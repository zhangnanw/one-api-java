package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.filter.Filter;
import com.oneapi.filter.ParamClamp;
import com.oneapi.handler.HeaderInjector;
import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.*;
import com.oneapi.relay.DefaultRelay;
import com.oneapi.relay.ModelSubstitutor;
import com.oneapi.service.CooldownService;
import com.oneapi.service.HolographicLogRecorder;
import com.oneapi.service.Redactor;
import com.oneapi.service.RelayLogger;
import com.oneapi.service.RouterService;
import com.oneapi.service.RouterService.RoutedVendor;
import com.oneapi.service.SessionTracker;
import com.oneapi.comparator.SorterFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * V2 中继协调器 — 5 阶段流水线。
 *
 * 阶段 1：RequestParser.parse(ctx) → RelayRequest（失败 → 400）
 * 阶段 2：过滤器链 stage2Filters 运行 RelayContext
 * 阶段 3：从 RouterService 加载候选 → stage3Filters 过滤
 * 阶段 4：Comparator.thenComparing 链排序
 * 阶段 5：relayChain.execute(first candidate, relayRequest)
 */
@Slf4j
public class RelayCoordinator {

    private final RouterService router;
    private final CooldownService cooldown;
    private final SessionTracker sessions;
    private final UpstreamClient upstreamClient;
    private final List<Filter> stage2Filters;
    private final List<Filter> stage3Filters;
    private final Comparator<RoutedVendor> sorter;
    private final DefaultRelay baseRelay;
    private final AppConfig config;
    private final HolographicLogRecorder holographicRecorder;
    private final Redactor redactor;

    public RelayCoordinator(RouterService router, CooldownService cooldown,
                            SessionTracker sessions,
                            UpstreamClient upstreamClient,
                            List<Filter> stage2Filters,
                            List<Filter> stage3Filters,
                            DefaultRelay baseRelay,
                            AppConfig config,
                            HolographicLogRecorder holographicRecorder,
                            Redactor redactor) {
        this.router = router;
        this.cooldown = cooldown;
        this.sessions = sessions;
        this.upstreamClient = upstreamClient;
        this.stage2Filters = stage2Filters;
        this.stage3Filters = stage3Filters;
        this.baseRelay = baseRelay;
        this.config = config;
        this.holographicRecorder = holographicRecorder;
        this.redactor = redactor;
        this.sorter = SorterFactory.build(config);
    }

    public void execute(RoutingContext ctx, byte[] rawBody) {
        // 第一阶段：解析
        RelayRequest req = RequestParser.parse(ctx, rawBody);
        if (req == null) {
            error(ctx, 400, "model name required");
            return;
        }
        ctx.put("modelName", req.requestedModel());

        // ── 全息日志：阶段1 ──
        var record = HolographicRecord.builder(UUID.randomUUID().toString())
            .timestampMs(System.currentTimeMillis())
            .requestedModel(req.requestedModel())
            .streaming(req.streaming())
            .bodySize(rawBody != null ? rawBody.length : 0)
            .clientIp(ctx.request().remoteAddress() != null
                ? ctx.request().remoteAddress().host() : "unknown")
            .redactor(redactor)
            .rawBody(rawBody)
            .buildInitial();
        ctx.put("holographicRecord", record);

        // 会话跟踪
        var messages = SessionTracker.parseMessages(req.rawBody());
        if (!messages.isEmpty()) {
            String sessionId = sessions.match(messages);
            ctx.put("sessionId", sessionId);
        }

        // 第二阶段：模型解析
        RelayContext relayCtx = new RelayContext(req.requestedModel());
        relayCtx.setRawBody(rawBody);
        for (var f : stage2Filters) {
            relayCtx = f.apply(relayCtx);
            if (relayCtx.hasError()) {
                error(ctx, relayCtx.error().httpStatus(), relayCtx.errorMessage());
                return;
            }
        }

        // ── 全息日志：阶段2 ──
        record.routingInfo(relayCtx);

        // 第三阶段：加载候选
        List<String> modelNames = relayCtx.modelNames();
        List<RoutedVendor> candidates;

        if (modelNames != null && !modelNames.isEmpty()) {
            // ModelsMatch：按列表顺序逐模型加载，instanceId 去重
            var seen = new HashSet<Integer>();
            candidates = new ArrayList<>();
            for (String name : modelNames) {
                for (var c : router.loadCandidates(name)) {
                    if (seen.add(c.instanceId())) {
                        candidates.add(c);
                    }
                }
            }
        } else {
            // 原路径：单 routingModelName
            String targetModel = relayCtx.routingModelName();
            if (targetModel == null || targetModel.isEmpty()) {
                targetModel = req.requestedModel();
            }
            candidates = router.loadCandidates(targetModel);
        }
        if (candidates.isEmpty()) {
            holographicRecorder.logNoCandidates(ctx, req.requestedModel());
            error(ctx, 503, "no available instances for " + req.requestedModel());
            return;
        }
        relayCtx.setCandidates(candidates);

        for (var f : stage3Filters) {
            relayCtx = f.apply(relayCtx);
        }

        // Check filter-set errors first (e.g. BodyLimitFilter 413)
        if (relayCtx.hasError()) {
            holographicRecorder.logStage3Error(ctx, relayCtx.error().httpStatus(), relayCtx.errorMessage());
            error(ctx, relayCtx.error().httpStatus(), relayCtx.errorMessage());
            return;
        }

        var filtered = relayCtx.candidates();
        if (filtered == null || filtered.isEmpty()) {
            holographicRecorder.logAllFiltered(ctx, req.requestedModel());
            error(ctx, 400, "all instances filtered for " + req.requestedModel());
            return;
        }

        // 软粘性 boost：同一会话优先用上次实例
        Object sidObj = ctx.get("sessionId");
        if (sidObj instanceof String sid && !sid.isEmpty()) {
            long preferredId = sessions.getPreferredInstance(sid).orElse(-1L);
            if (preferredId > 0) {
                filtered = new ArrayList<>(filtered);
                for (int i = 0; i < filtered.size(); i++) {
                    if (filtered.get(i).instanceId() == preferredId) {
                        var prev = filtered.remove(i);
                        filtered.add(0, prev);
                        break;
                    }
                }
            }
        }

        // 第四阶段：排序
        LinkedList<RoutedVendor> queue = new LinkedList<>(filtered);
        queue.sort(sorter);

        // ── 全息日志：阶段3-4 ──
        record.candidatesInfo(candidates, relayCtx.filterLog(), queue);

        // 第五阶段：中继
        ctx.put("relayContext", relayCtx);
        executePipeline(ctx, req, queue, relayCtx);
    }

    /**
     * 调度：按 streaming 标志分派到 buffered 或 stream 路径。
     * Pure dispatcher — 不直接中继、不持状态。
     */
    private void executePipeline(RoutingContext ctx, RelayRequest req,
                                  LinkedList<RoutedVendor> queue, RelayContext relayCtx) {
        if (req.streaming()) {
            tryStream(ctx, req, queue, relayCtx);
        } else {
            tryBuffered(ctx, req, queue);
        }
    }

    // --- 缓冲式中继：遍历候选队列，逐个尝试 ---

    /**
     * 非流式中继：用自我尾调用遍历候选队列，逐个尝试，失败则重试下一个（等价于 while 循环，避免递归栈累积）。
     * 队列空时返回 503；非空则取队首，异步执行，失败后通过 tryBuffered 自我调用继续尝试下一个。
     */
    private void tryBuffered(RoutingContext ctx, RelayRequest req,
                              LinkedList<RoutedVendor> queue) {
        // 队列空 = 所有候选都失败了
        if (queue.isEmpty()) {
            holographicRecorder.logAllFailed(ctx, 503, "no available instances for " + req.requestedModel());
            error(ctx, 503, "no available instances for " + req.requestedModel());
            return;
        }

        RoutedVendor routedVendor = queue.removeFirst();
        Candidate candidate = routedVendor.toCandidate();
        String vendorName = routedVendor.vendor() != null ? routedVendor.vendor().getName() : "?";

        // Reasoning 模式 + Kimi UA（供应商专用头）
        var rc = ctx.get("relayContext");
        injectVendorSpecificHeaders(candidate, routedVendor.vendor(), rc instanceof RelayContext relayCtx ? relayCtx : null);

        log.info("V2 trying: instance={} vendor={} model={} upstream={}",
            routedVendor.instanceId(), vendorName, routedVendor.modelName(), routedVendor.upstreamModel());

        long startMs = System.currentTimeMillis();

        byte[] finalBody = ParamClamp.clamp(
            substituteModel(req.rawBody(), routedVendor.upstreamModel(), req.requestedModel()),
            MetaView.fromInstanceMeta(routedVendor.instanceMeta()).instanceCaps());
        RelayRequest finalReq = new RelayRequest(req.requestedModel(), finalBody, false);

        // 日志失败时用哨兵值 -1，统一到 onComplete 回调，消除 4 lambda 重复
        startLogAsync(ctx, req, candidate)
            .otherwise(err -> { log.error("relay log start failed: {}", err.getMessage()); return -1L; })
            .onComplete(ar -> {
                long logId = ar.result();

                baseRelay.execute(candidate, finalReq)
                    .onSuccess(result -> {
                        if (logId >= 0) {
                            updateStreamResultAsync(ctx, logId, result.httpStatus(),
                                result.promptTokens() + result.completionTokens(),
                                System.currentTimeMillis() - startMs, null);
                        }
                        Object sidObj = ctx.get("sessionId");
                        if (sidObj instanceof String sid && !sid.isEmpty()) {
                            sessions.recordInstance(sid, routedVendor.instanceId());
                        }
                        long lat = System.currentTimeMillis() - startMs;
                        holographicRecorder.logAttemptSuccessBuffered(ctx, vendorName, routedVendor.instanceId(),
                            routedVendor.upstreamModel(),
                            vendorEndpoint(routedVendor),
                            200, lat,
                            result.promptTokens(), result.completionTokens(),
                            result.responseBody());
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(result.responseBody());
                    })
                    .onFailure(err -> {
                        long latency = System.currentTimeMillis() - startMs;
                        int status = extractHttpStatus(err);
                        String errMsg = extractErrorMessage(err);

                        log.warn("{} from vendor={}, try next ({} left)",
                            status, vendorName, queue.size());
                        if (routedVendor.vendor() != null) {
                            if (status == 429 || status == 403) {
                                cooldown.setVendorCooldown(routedVendor.vendor().getId());
                            } else if (status == 200) {
                                cooldown.setInstanceCooldown(
                                    routedVendor.instanceId(), routedVendor.instanceTags());
                            }
                        }
                        if (logId >= 0) {
                            updateStreamResultAsync(ctx, logId, status, 0, latency, errMsg);
                        }
                        holographicRecorder.logAttemptFailure(ctx, vendorName, routedVendor.instanceId(),
                            routedVendor.upstreamModel(),
                            vendorEndpoint(routedVendor),
                            status, latency, errorTypeFromStatus(status), errMsg,
                            routedVendor.vendor() != null && (status == 429 || status == 403 || status == 200));
                        tryBuffered(ctx, req, queue);
                    });
            });
    }

    /** 流式中继：只试队列中第一个候选，失败不重试（流式重试会导致客户端收到重复数据）。 */
    private void tryStream(RoutingContext ctx, RelayRequest req,
                            LinkedList<RoutedVendor> queue, RelayContext relayCtx) {
        relayStream(ctx, req, queue, relayCtx);
    }

    private static int extractHttpStatus(Throwable err) {
        return RelayErrorHelper.extractHttpStatus(err);
    }

    /**
     * 提取上游错误消息。仅在所有候选耗尽后暴露给客户端。
     * 有备用实例时，此消息仅写入日志，不返回给客户端。
     */
    private static String extractErrorMessage(Throwable err) {
        return RelayErrorHelper.extractErrorMessage(err);
    }

    // --- 流式中继：遍历候选队列直到成功 ---

    private void relayStream(RoutingContext ctx, RelayRequest req,
                             LinkedList<RoutedVendor> queue,
                             RelayContext relayCtx) {
        if (queue.isEmpty()) {
            holographicRecorder.logAllFailed(ctx, 503, "no available instances for " + req.requestedModel());
            error(ctx, 503, "no available instances for " + req.requestedModel());
            return;
        }
        // 跳过 vendor 为 null 的候选（防御性）
        while (!queue.isEmpty() && queue.getFirst().vendor() == null) {
            log.warn("stream: vendor null for instance={}, skip", queue.removeFirst().instanceId());
        }
        if (queue.isEmpty()) {
            error(ctx, 503, "V2 stream: no candidates left");
            return;
        }
        RoutedVendor first = queue.removeFirst();

        Candidate candidate = first.toCandidate();

        // 供应商专用头（Reasoning + Kimi UA）
        injectVendorSpecificHeaders(candidate, first.vendor(), relayCtx);

        long startMs = System.currentTimeMillis();
        byte[] finalBody = ParamClamp.clamp(
            substituteModel(req.rawBody(), first.upstreamModel(), req.requestedModel()),
            MetaView.fromInstanceMeta(first.instanceMeta()).instanceCaps());

        var relayReq = new UpstreamClient.OutboundRequest(
            first.vendor().getBaseUrl(),
            first.vendor().getApiKey(),
            "/v1/chat/completions",
            ctx.request().method().name(),
            finalBody,
            candidate.extraHeaders(),
            ctx.response());

        String reqId = ctx.get("sessionId") != null ? (String) ctx.get("sessionId") : "";
        log.info("[req={}] stream relay -> {} #{} model={}",
            reqId, first.vendor().getName(), first.instanceId(), first.upstreamModel());

        startLogAsync(ctx, req, candidate)
            .otherwise(err -> { log.error("relay log start failed: {}", err.getMessage()); return -1L; })
            .onComplete(ar -> {
                long logId = ar.result();

                upstreamClient.relayStream(relayReq,
                    statusCode -> statusCode == 200,
                    (statusCode, tokens) -> {
                        long latency = System.currentTimeMillis() - startMs;

                        if (statusCode == 200) {
                            cooldown.clearCooldown(first.instanceId(), first.vendor().getId());
                            Object sidObj = ctx.get("sessionId");
                            if (sidObj instanceof String sid && !sid.isEmpty()) {
                                sessions.recordInstance(sid, first.instanceId());
                            }
                            if (logId >= 0) {
                                updateStreamResultAsync(ctx, logId, statusCode, tokens, latency, null);
                            }
                            holographicRecorder.logAttemptSuccess(ctx, first.vendor().getName(), first.instanceId(),
                                first.upstreamModel(),
                                vendorEndpoint(first),
                                statusCode, System.currentTimeMillis() - startMs, tokens);
                        } else {
                            cooldown.setVendorCooldown(first.vendor().getId());
                            if (logId >= 0) {
                                updateStreamResultAsync(ctx, logId, statusCode, 0, latency, "");
                            }
                            holographicRecorder.logAttemptFailure(ctx, first.vendor().getName(), first.instanceId(),
                                first.upstreamModel(),
                                vendorEndpoint(first),
                                statusCode, System.currentTimeMillis() - startMs,
                                errorTypeFromStatus(statusCode), "stream upstream error", true);

                            if (!queue.isEmpty()) {
                                relayStream(ctx, req, queue, relayCtx);
                            } else {
                                holographicRecorder.logAllFailed(ctx, statusCode, "all upstream instances failed");
                                error(ctx, 502, "all upstream instances failed");
                            }
                        }
                    },
                    null
                );
            });
    }

    // --- 辅助方法 ---

    private static String errorTypeFromStatus(int status) {
        return RelayErrorHelper.errorTypeFromStatus(status);
    }

    /** 拼接 vendor baseUrl + chat completions 端点；vendor 为 null 时返空串。 */
    private static String vendorEndpoint(RoutedVendor rv) {
        if (rv.vendor() == null) return "";
        return rv.vendor().getBaseUrl() + "/v1/chat/completions";
    }

    /** 替换 JSON 请求体中的 "model" 字段为上游模型名称。 */

    /** 注入供应商专用头（X-Reasoning-Effort + Kimi CLI User-Agent）。 */
    private static void injectVendorSpecificHeaders(Candidate candidate, Vendor vendor, RelayContext relayCtx) {
        HeaderInjector.inject(candidate, vendor, relayCtx);
    }

    /** 替换 JSON 请求体中的 "model" 字段为上游模型名称。 */
    private static byte[] substituteModel(byte[] rawBody, String upstreamModel, String srcModel) {
        return ModelSubstitutor.substitute(rawBody, upstreamModel, srcModel);
    }

    // --- 异步 Relay 日志辅助方法 ---

    private static Future<Long> startLogAsync(RoutingContext ctx, RelayRequest req, Candidate candidate) {
        return ctx.vertx().executeBlocking(() -> {
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
        });
    }

    private static void updateStreamResultAsync(RoutingContext ctx, long logId, int code, int tokens, long latencyMs, String err) {
        ctx.vertx().executeBlocking(() -> {
            RelayLogger.updateStreamResult(logId, code, tokens, latencyMs, err);
            return null;
        }).onFailure(e -> log.error("relay log update failed: {}", e.getMessage()));
    }

    private static void error(RoutingContext ctx, int code, String msg) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(code)
            .end(new JsonObject()
                .put("error", new JsonObject()
                    .put("message", msg)
                    .put("type", "relay_error")
                    .put("code", code))
                .toString());
    }
}
