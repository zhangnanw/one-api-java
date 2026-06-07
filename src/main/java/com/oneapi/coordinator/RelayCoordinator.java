package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.filter.Filter;
import com.oneapi.filter.ParamClamp;
import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.*;
import com.oneapi.relay.DefaultRelay;
import com.oneapi.service.CooldownService;
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

        // 会话跟踪
        var messages = SessionTracker.parseMessages(req.rawBody());
        if (!messages.isEmpty()) {
            String sessionId = sessions.match(messages);
            ctx.put("sessionId", sessionId);
        }

        // 第二阶段：模型解析
        RelayContext relayCtx = new RelayContext(req.requestedModel());
        for (var f : stage2Filters) {
            relayCtx = f.apply(relayCtx);
            if (relayCtx.hasError()) {
                error(ctx, relayCtx.error().httpStatus(), relayCtx.errorMessage());
                return;
            }
        }

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
            // 原路径：单 upstreamModel
            String targetModel = relayCtx.upstreamModel();
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

        var filtered = relayCtx.<RoutedVendor>candidates();
        if (filtered == null || filtered.isEmpty()) {
            error(ctx, 503, "all instances filtered for " + req.requestedModel());
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

    /** 非流式中继：遍历候选队列，重试直到成功。 */
    private void tryBuffered(RoutingContext ctx, RelayRequest req,
                              LinkedList<RoutedVendor> queue) {
        relayBufferedInner(ctx, req, queue, null);
    }

    /** 流式中继：只试第一个候选，失败由外层 caller 处理（当前实现不重试流式）。 */
    private void tryStream(RoutingContext ctx, RelayRequest req,
                            LinkedList<RoutedVendor> queue, RelayContext relayCtx) {
        relayStream(ctx, req, queue, relayCtx);
    }

    // --- 缓冲式中继：遍历候选队列，逐个尝试 ---

    private void relayBuffered(RoutingContext ctx, RelayRequest req,
                               LinkedList<RoutedVendor> queue) {
        relayBufferedInner(ctx, req, queue, null);
    }

    private void relayBufferedInner(RoutingContext ctx, RelayRequest req,
                                    LinkedList<RoutedVendor> queue,
                                    String lastUpstreamError) {
        if (queue.isEmpty()) {
            String msg = lastUpstreamError != null
                ? lastUpstreamError
                : "no available instances for " + req.requestedModel();
            error(ctx, 503, msg);
            return;
        }

        RoutedVendor routedVendor = queue.removeFirst();
        Candidate candidate = routedVendor.toCandidate();
        String vendorName = routedVendor.vendor() != null ? routedVendor.vendor().getName() : "?";

        // Reasoning 头部
        var relayCtxObj = ctx.get("relayContext");
        if (relayCtxObj instanceof RelayContext rc && rc.reasoning()) {
            candidate.extraHeaders().add("X-Reasoning-Effort", "max");
        }

        // Kimi API 需要 CLI UA
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
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(result.responseBody());
            })
            .onFailure(err -> {
                long latency = System.currentTimeMillis() - startMs;
                int status = extractHttpStatus(err);
                String errMsg = extractErrorMessage(err);

                // 重试策略：任何上游错误都试下一个候选，队空才暴露上游错误给客户端。
                // 设计意图：有备用实例时静默切换，仅最后一个候选失败才返回错误。
                log.warn("{} from vendor={}, try next ({} left)",
                    status, vendorName, queue.size());
                if (status == 429 && routedVendor.vendor() != null) {
                    cooldown.setVendorCooldown(routedVendor.vendor().getId());
                }
                recorder.fail(logId, status, errMsg, latency);
                relayBufferedInner(ctx, req, queue, errMsg);
            });
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

        // 流式路径不重试：一旦 chunk 开始返回客户端，无法切换候选。
        // 无后续实例时，上游错误通过 HTTP 状态码和响应体暴露给客户端。
        upstreamClient.relayStream(relayReq, (statusCode, tokens) -> {
            long latency = System.currentTimeMillis() - startMs;
            if (statusCode < 500) {
                cooldown.clearCooldown(first.instanceId(), first.vendor().getId());
                Object sidObj = ctx.get("sessionId");
                if (sidObj instanceof String sid && !sid.isEmpty()) {
                    sessions.recordInstance(sid, first.instanceId());
                }
            } else {
                cooldown.setInstanceCooldown(first.instanceId(), first.instanceTags());
            }
            recorder.completeStream(logId, statusCode, tokens, latency);
        }, null);
    }

    // --- 辅助方法 ---

    /** 替换 JSON 请求体中的 "model" 字段为上游模型名称。 */

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

    private static Comparator<RoutedVendor> buildSorter(AppConfig config) {
        // pref 排序 = 基础 pref + layer 偏移（free+0, subscription+10000, payg+20000）
        return new ByPref()
            .thenComparing(new ByStatusDesc());
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
