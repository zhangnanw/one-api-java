package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.filter.Filter;
import com.oneapi.filter.ParamClamp;
import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.*;
import com.oneapi.relay.DefaultRelay;
import com.oneapi.service.CooldownService;
import com.oneapi.service.HolographicLogger;
import com.oneapi.service.RouterService;
import com.oneapi.service.RouterService.RoutedVendor;
import com.oneapi.service.SessionTracker;
import com.oneapi.comparator.ById;
import com.oneapi.comparator.ByPref;
import com.oneapi.comparator.ByStatusDesc;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * V2 中继协调器 — 5 阶段流水线。
 *
 * 阶段 1：RequestParser.parse(ctx) → RelayRequest（失败 → 400）
 * 阶段 2：过滤器链 stage2Filters 运行 RelayContext
 * 阶段 3：从 RouterService 加载候选 → stage3Filters 过滤
 * 阶段 4：Comparator.thenComparing 链排序
 * 阶段 5：relayChain.execute(first candidate, relayRequest)
 */
public class RelayCoordinator {
    private static final Logger log = LoggerFactory.getLogger(RelayCoordinator.class);

    private final RouterService router;
    private final CooldownService cooldown;
    private final SessionTracker sessions;
    private final UpstreamClient upstreamClient;
    private final List<Filter> stage2Filters;
    private final List<Filter> stage3Filters;
    private final Comparator<RoutedVendor> sorter;
    private final DefaultRelay baseRelay;
    private final AppConfig config;
    private final RelayRecorder recorder;

    public RelayCoordinator(RouterService router, CooldownService cooldown,
                            SessionTracker sessions,
                            UpstreamClient upstreamClient,
                            List<Filter> stage2Filters,
                            List<Filter> stage3Filters,
                            DefaultRelay baseRelay,
                            AppConfig config) {
        this.router = router;
        this.cooldown = cooldown;
        this.sessions = sessions;
        this.upstreamClient = upstreamClient;
        this.stage2Filters = stage2Filters;
        this.stage3Filters = stage3Filters;
        this.baseRelay = baseRelay;
        this.config = config;
        this.recorder = new RelayRecorder();
        this.sorter = buildSorter(config);
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

        // ── 全息日志：阶段2 完成（模型解析结果）──
        record.routingInfo(relayCtx);

        // 第三阶段：加载候选
        List<String> modelNames = relayCtx.modelNames();
        List<RoutedVendor> candidates;

        if (modelNames != null && !modelNames.isEmpty()) {
        // ModelsMatch：按模型名列表顺序逐模型加载候选，instanceId 去重（同一实例只出现一次）
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
        // 原路径：按 routingModelName 加载单个模型的候选列表
            String targetModel = relayCtx.routingModelName();
            if (targetModel == null || targetModel.isEmpty()) {
                targetModel = req.requestedModel();
            }
            candidates = router.loadCandidates(targetModel);
        }
        if (candidates.isEmpty()) {
            error(ctx, 503, "no available instances for " + req.requestedModel());
            return;
        }
        relayCtx.setCandidates(candidates);

        for (var f : stage3Filters) {
            relayCtx = f.apply(relayCtx);
        }

        // 先检查 filter 是否设置了错误（如 BodyLimitFilter 的 413）
        if (relayCtx.hasError()) {
            error(ctx, relayCtx.error().httpStatus(), relayCtx.errorMessage());
            return;
        }

        var filtered = relayCtx.<RoutedVendor>candidates();
        if (filtered == null || filtered.isEmpty()) {
            error(ctx, 400, "all instances filtered for " + req.requestedModel());
            return;
        }

        // 会话粘性：同一 sessionId 的连续请求优先路由到上次使用的实例
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

        // ── 全息日志：阶段3（候选列表）、阶段4（排序结果）──
        record.candidatesInfo(candidates, relayCtx.filterLog(), queue);

        // 第五阶段：中继
        ctx.put("relayContext", relayCtx);
        executePipeline(ctx, req, queue, relayCtx);
    }

    /**
     * 调度器 — 根据 streaming 标志分派到缓冲式或流式路径。
     * 纯分发逻辑，不直接发起请求、不持有状态。
     */
    private void executePipeline(RoutingContext ctx, RelayRequest req,
                                  LinkedList<RoutedVendor> queue, RelayContext relayCtx) {
        if (req.streaming()) {
            tryStream(ctx, req, queue, relayCtx);
        } else {
            tryBuffered(ctx, req, queue);
        }
    }

    /** 非流式中继：用 while 循环遍历候选队列，逐个尝试，失败则重试下一个。 */
    private void tryBuffered(RoutingContext ctx, RelayRequest req,
                              LinkedList<RoutedVendor> queue) {
        // 队列空 = 所有候选都失败了
        if (queue.isEmpty()) {
            String msg = "no available instances for " + req.requestedModel();
            var rec = ctx.<HolographicRecord>get("holographicRecord");
            if (rec != null) {
                long totalMs = System.currentTimeMillis() - rec.startMs();
                rec.finish("failure", 503, totalMs, 0, null,
                    "relay", msg, rec.attemptCount(), null, 0);
                HolographicLogger.write(rec);
            }
            error(ctx, 503, msg);
            return;
        }

        RoutedVendor routedVendor = queue.removeFirst();
        Candidate candidate = routedVendor.toCandidate();
        String vendorName = routedVendor.vendor() != null ? routedVendor.vendor().getName() : "?";

        // Reasoning 模式
        var rc = ctx.get("relayContext");
        if (rc instanceof RelayContext relayCtx && relayCtx.reasoning()) {
            candidate.extraHeaders().add("X-Reasoning-Effort", "max");
        }

        // Kimi API 要求 User-Agent 必须为 KimiCLI/1.6，否则返回 403
        if (routedVendor.vendor() != null && routedVendor.vendor().getBaseUrl() != null
                && routedVendor.vendor().getBaseUrl().contains("kimi.com")) {
            candidate.extraHeaders().add("User-Agent", "KimiCLI/1.6");
        }

        log.info("V2 trying: instance={} vendor={} model={} upstream={}",
            routedVendor.instanceId(), vendorName, routedVendor.modelName(), routedVendor.upstreamModel());

        long logId = recorder.start(req, candidate);
        long startMs = System.currentTimeMillis();

        byte[] finalBody = ParamClamp.clamp(
            substituteModel(req.rawBody(), routedVendor.upstreamModel(), req.requestedModel()),
            MetaView.fromInstanceMeta(routedVendor.instanceMeta()).instanceCaps());
        RelayRequest finalReq = new RelayRequest(req.requestedModel(), finalBody, false);

        baseRelay.execute(candidate, finalReq)
            .onSuccess(result -> {
                recorder.complete(logId, result, startMs);
                Object sidObj = ctx.get("sessionId");
                if (sidObj instanceof String sid && !sid.isEmpty()) {
                    sessions.recordInstance(sid, routedVendor.instanceId());
                }
                var rec = ctx.<HolographicRecord>get("holographicRecord");
                if (rec != null) {
                    long lat = System.currentTimeMillis() - startMs;
                    String upstreamUrl = routedVendor.vendor() != null
                        ? routedVendor.vendor().getBaseUrl() + "/v1/chat/completions" : "";
                    rec.addAttempt(HolographicRecord.AttemptRecord.success(
                        vendorName, routedVendor.instanceId(),
                        routedVendor.upstreamModel(), upstreamUrl,
                        200, lat, 0,
                        result.promptTokens(), result.completionTokens()));
                    long totalMs = System.currentTimeMillis() - rec.startMs();
                    int totalTokens = result.promptTokens() + result.completionTokens();
                    rec.finish("success", 200, totalMs, totalTokens,
                        result.responseBody(), null, null, rec.attemptCount(),
                        vendorName, routedVendor.instanceId());
                    HolographicLogger.write(rec);
                }
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
                if (routedVendor.vendor() != null && (status == 429 || status == 403 || status == 200)) {
                    cooldown.setVendorCooldown(routedVendor.vendor().getId());
                }
                recorder.fail(logId, status, errMsg, latency);
                var rec = ctx.<HolographicRecord>get("holographicRecord");
                if (rec != null) {
                    String upstreamUrl = routedVendor.vendor() != null
                        ? routedVendor.vendor().getBaseUrl() + "/v1/chat/completions" : "";
                    boolean cooled = routedVendor.vendor() != null
                        && (status == 429 || status == 403 || status == 200);
                    rec.addAttempt(HolographicRecord.AttemptRecord.failure(
                        vendorName, routedVendor.instanceId(),
                        routedVendor.upstreamModel(), upstreamUrl,
                        status, latency, errorTypeFromStatus(status), errMsg, cooled));
                }
                // while 循环重试下一个候选（尾部调用，等价于迭代）
                tryBuffered(ctx, req, queue);
            });
    }

    /** 流式中继：只试队列中第一个候选，失败不重试（流式重试会导致客户端收到重复数据）。 */
    private void tryStream(RoutingContext ctx, RelayRequest req,
                            LinkedList<RoutedVendor> queue, RelayContext relayCtx) {
        relayStream(ctx, req, queue, relayCtx);
    }

    private static int extractHttpStatus(Throwable err) {
        if (err instanceof RelayException re
                && re.getError() instanceof RelayError.UpstreamFailure upstreamFailure) {
            return upstreamFailure.httpCode();
        }
        return 503;
    }

    /**
     * 提取上游错误消息。仅在所有候选耗尽后暴露给客户端。
     * 有备用实例时，此消息仅写入日志，不返回给客户端。
     */
    private static String extractErrorMessage(Throwable err) {
        if (err instanceof RelayException re
                && re.getError() instanceof RelayError.UpstreamFailure upstreamFailure) {
            String body = upstreamFailure.responseBody();
            if (body != null) {
                try {
                    var json = new JsonObject(body);
                    var errorNode = json.getJsonObject("error");
                    if (errorNode != null && errorNode.getString("message") != null) {
                        return "upstream: " + errorNode.getString("message");
                    }
                } catch (Exception ignored) {}
            }
            return "upstream " + upstreamFailure.httpCode();
        }
        return err.getMessage() != null ? err.getMessage() : "relay failed";
    }

    // --- 流式中继：遍历候选队列直到成功 ---

    private void relayStream(RoutingContext ctx, RelayRequest req,
                             LinkedList<RoutedVendor> queue,
                             RelayContext relayCtx) {
        if (queue.isEmpty()) {
            // ── 全息日志：流式无候选 ──
            var rec = ctx.<HolographicRecord>get("holographicRecord");
            if (rec != null) {
                long totalMs = System.currentTimeMillis() - rec.startMs();
                rec.finish("failure", 503, totalMs, 0, null,
                    "relay", "no available instances", 0, null, 0);
                HolographicLogger.write(rec);
            }
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

        // Kimi API 需要 CLI UA
        if (first.vendor().getBaseUrl() != null
                && first.vendor().getBaseUrl().contains("kimi.com")) {
            candidate.extraHeaders().add("User-Agent", "KimiCLI/1.6");
        }

        // Reasoning 头部
        if (relayCtx.reasoning()) {
            candidate.extraHeaders().add("X-Reasoning-Effort", "max");
        }

        long logId = recorder.start(req, candidate);
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

        ctx.response().setChunked(true);

        // 流式递归 fallback：只有 HTTP 200 才往客户端 pipe；非 200 触发冷却并试下一个候选
        upstreamClient.relayStream(relayReq,
            statusCode -> statusCode == 200,  // 只有 200 才 pipe
            (statusCode, tokens) -> {
                long latency = System.currentTimeMillis() - startMs;
                var rec = ctx.<HolographicRecord>get("holographicRecord");
                
                if (statusCode == 200) {
                    cooldown.clearCooldown(first.instanceId(), first.vendor().getId());
                    Object sidObj = ctx.get("sessionId");
                    if (sidObj instanceof String sid && !sid.isEmpty()) {
                        sessions.recordInstance(sid, first.instanceId());
                    }
                    recorder.completeStream(logId, statusCode, tokens, latency);
                    
                    if (rec != null) {
                        String upstreamUrl = first.vendor().getBaseUrl() + "/v1/chat/completions";
                        String vname = first.vendor().getName();
                        rec.addAttempt(HolographicRecord.AttemptRecord.success(
                            vname, first.instanceId(), first.upstreamModel(), upstreamUrl,
                            statusCode, latency, 0, 0, tokens));
                        long totalMs = System.currentTimeMillis() - rec.startMs();
                        rec.finish("success", statusCode, totalMs, tokens,
                            null, null, null, rec.attemptCount(),
                            vname, first.instanceId());
                        HolographicLogger.write(rec);
                    }
                } else {
                    // 任何非 200 → 冷却该供应商 → 递归试下一个候选
                    cooldown.setVendorCooldown(first.vendor().getId());
                    recorder.fail(logId, statusCode, "", latency);
                    
                    if (rec != null) {
                        String upstreamUrl = first.vendor().getBaseUrl() + "/v1/chat/completions";
                        String vname = first.vendor().getName();
                        rec.addAttempt(HolographicRecord.AttemptRecord.failure(
                            vname, first.instanceId(), first.upstreamModel(), upstreamUrl,
                            statusCode, latency, errorTypeFromStatus(statusCode),
                            "stream upstream error", true));
                    }
                    
                    // 流式请求不递归重试：一旦开始 pipe 部分数据，客户端收到的流会损坏
                    if (rec != null) {
                        long totalMs = System.currentTimeMillis() - rec.startMs();
                        rec.finish("failure", statusCode, totalMs, 0,
                            null, "relay", "all upstream instances failed",
                            rec.attemptCount(), null, 0);
                        HolographicLogger.write(rec);
                    }
                    error(ctx, 502, "all upstream instances failed");
                }
            },
            null  // chunkConverter: 不需要
        );
    }

    // --- 辅助方法 ---

    private static String errorTypeFromStatus(int status) {
        return switch (status) {
            case 429 -> "rate_limited";
            case 403 -> "forbidden";
            case 502 -> "bad_gateway";
            case 503 -> "service_unavailable";
            case 504 -> "gateway_timeout";
            case 200 -> "empty_response";
            default -> "http_" + status;
        };
    }

    /** 将请求体中的 model 字段替换为上游实际模型名（如果不同）。 */

    private static byte[] substituteModel(byte[] rawBody, String upstreamModel, String srcModel) {
        if (upstreamModel == null || upstreamModel.equals(srcModel)) return rawBody;
        try {
            JsonObject json = new JsonObject(new String(rawBody));
            json.put("model", upstreamModel);
            return json.encode().getBytes();
        } catch (Exception ex) {
            return rawBody;
        }
    }

    /**
     * 构建候选排序器。
     * 优先级：pref（供应商优先级）> status（活跃实例优先于不活跃的）。
     * layer 偏移由 RouterService 在加载候选时已经处理（free+0, subscription+10000, payg+20000）。
     */
    private static Comparator<RoutedVendor> buildSorter(AppConfig config) {
        return new ByPref()
            .thenComparing(new ByStatusDesc())
            .thenComparing(new ById());
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
