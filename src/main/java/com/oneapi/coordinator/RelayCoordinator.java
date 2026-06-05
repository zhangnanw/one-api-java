package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.filter.Filter;
import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.*;
import com.oneapi.relay.RelayExecutor;
import com.oneapi.service.CooldownService;
import com.oneapi.service.RouterService;
import com.oneapi.service.RouterService.RoutedVendor;
import com.oneapi.service.SessionTracker;
import com.oneapi.sort.*;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: V2.1 流式装饰器链
// 当前流式传输绕过了装饰器链（Header/Reasoning/Retry）
// 因为流式响应无法表示为 Future<RelayResult>。
// 下面的 hack 在 relayStream() 中手动应用了 Header + Reasoning 逻辑。

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
    private final RelayExecutor relayChain;
    private final AppConfig config;
    private final RelayRecorder recorder;

    public RelayCoordinator(RouterService router, CooldownService cooldown,
                            SessionTracker sessions,
                            UpstreamClient upstreamClient,
                            List<Filter> stage2Filters,
                            List<Filter> stage3Filters,
                            RelayExecutor relayChain,
                            AppConfig config) {
        this.router = router;
        this.cooldown = cooldown;
        this.sessions = sessions;
        this.upstreamClient = upstreamClient;
        this.stage2Filters = stage2Filters;
        this.stage3Filters = stage3Filters;
        this.relayChain = relayChain;
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
        ctx.put("model_name", req.requestedModel());

        // 会话跟踪
        var messages = SessionTracker.parseMessages(req.rawBody());
        if (!messages.isEmpty()) {
            String sessionId = sessions.match(messages);
            ctx.put("session_id", sessionId);
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

        // 解析目标模型名称（来自第二阶段解析结果）
        String targetModel = relayCtx.upstreamModel();
        if (targetModel == null || targetModel.isEmpty()) {
            targetModel = req.requestedModel();
        }

        // 第三阶段：加载候选 + 过滤
        var candidates = router.loadCandidates(targetModel);
        if (candidates.isEmpty()) {
            error(ctx, 503, "no available instances for " + targetModel);
            return;
        }
        relayCtx.setCandidates(candidates);

        for (var f : stage3Filters) {
            relayCtx = f.apply(relayCtx);
        }

        var filtered = relayCtx.<RoutedVendor>candidates();
        if (filtered == null || filtered.isEmpty()) {
            error(ctx, 503, "all instances filtered for " + targetModel);
            return;
        }

        // 第四阶段：排序
        List<RoutedVendor> mutable = new ArrayList<>(filtered);
        mutable.sort(sorter);
        var first = mutable.get(0);

        log.info("V2 selected: instance={} vendor={} model={} upstream={}",
            first.instanceId(), first.vendor() != null ? first.vendor().getName() : "?",
            first.modelName(), first.upstreamModel());

        // 从 RoutedVendor 构建 Candidate
        Candidate candidate = toCandidate(first);

        // Reasoning 头部（由 VirtualModelLookup 设置）
        if (relayCtx.reasoning()) {
            candidate.extraHeaders().put("X-Reasoning-Effort", "max");
        }

        // 启动中继日志
        long logId = recorder.start(req, candidate);
        long startMs = System.currentTimeMillis();

        // 第五阶段：中继
        if (req.isStreaming()) {
            relayStream(ctx, req, candidate, first, relayCtx, logId, startMs);
        } else {
            relayBuffered(ctx, req, candidate, first, logId, startMs);
        }
    }

    // --- 缓冲式中继（通过中继链支持重试）---

    private void relayBuffered(RoutingContext ctx, RelayRequest req,
                               Candidate candidate, RoutedVendor first,
                               long logId, long startMs) {

        // 替换请求体中的模型名称以匹配上游模型
        byte[] finalBody = substituteModel(req.rawBody(), first.upstreamModel(), first.modelName());
        RelayRequest finalReq = new RelayRequest(req.requestedModel(), finalBody,
            new String(finalBody), false);

        relayChain.execute(candidate, finalReq)
            .onSuccess(result -> {
                recorder.complete(logId, result, startMs);
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(result.responseBody());
            })
            .onFailure(err -> {
                long latency = System.currentTimeMillis() - startMs;
                int code = 503;
                String errMsg = err.getMessage() != null ? err.getMessage() : "relay failed";
                if (err instanceof RelayException re) {
                    code = re.getError().httpStatus();
                    if (re.getError() instanceof RelayError.UpstreamFailure uf) {
                        String body = uf.responseBody();
                        // 尝试从上游 JSON 响应中提取错误消息
                        try {
                            var respJson = new JsonObject(body);
                            var errorObj = respJson.getJsonObject("error");
                            if (errorObj != null && errorObj.getString("message") != null) {
                                errMsg = "upstream: " + errorObj.getString("message");
                            } else {
                                errMsg = "upstream " + uf.httpCode();
                            }
                        } catch (Exception e) {
                            errMsg = "upstream " + uf.httpCode();
                        }
                    } else {
                        errMsg = re.getMessage() != null ? re.getMessage() : errMsg;
                    }
                }
                recorder.fail(logId, code, errMsg, latency);
                error(ctx, code, errMsg);
            });
    }

    // --- 流式中继 ---
    // TODO: V2.1 — 通过真正的装饰器链应用 Header + Reasoning，
    // 而不是在这里复制粘贴逻辑。

    private void relayStream(RoutingContext ctx, RelayRequest req,
                             Candidate candidate, RoutedVendor first,
                             RelayContext relayCtx,
                             long logId, long startMs) {
        // 替换请求体中的模型名称
        byte[] finalBody = substituteModel(req.rawBody(), first.upstreamModel(), first.modelName());

        var extraHeaders = MultiMap.caseInsensitiveMultiMap();
        candidate.extraHeaders().forEach(extraHeaders::add);

        // HACK: kimi.com UA（与 HeaderDecorator 相同）
        if (first.vendor() != null && first.vendor().getBaseUrl() != null
            && first.vendor().getBaseUrl().contains("kimi.com")) {
            extraHeaders.set("User-Agent", "KimiCLI/1.6");
        }

        // Reasoning 头部（与 ReasoningDecorator 相同）
        if (relayCtx.reasoning()) {
            extraHeaders.set("X-Reasoning-Effort", "max");
        }

        var relayReq = new UpstreamClient.RelayRequest(
            first.vendor().getBaseUrl(),
            first.vendor().getApiKey(),
            ctx.request().path(),
            ctx.request().method().name(),
            finalBody,
            extraHeaders,
            ctx.response());

        String reqId = ctx.get("req_id") != null ? (String) ctx.get("req_id") : "";
        log.info("[req={}] stream relay -> {} #{} model={}",
            reqId, first.vendor().getName(), first.instanceId(), first.upstreamModel());

        ctx.response().setChunked(true);

        upstreamClient.relayStream(relayReq, (statusCode, tokens) -> {
            long latency = System.currentTimeMillis() - startMs;
            if (statusCode < 500) {
                cooldown.clearCooldown(first.instanceId(), first.vendor().getId());
            } else {
                cooldown.setInstanceCooldown(first.instanceId(), first.instanceTags());
            }
            if (logId > 0) {
                recorder.completeStream(logId, statusCode, tokens, latency);
            }
        });
    }

    // --- 辅助方法 ---

    private Candidate toCandidate(RoutedVendor rv) {
        Instance inst = new Instance();
        inst.setId(rv.instanceId());
        inst.setModelName(rv.modelName());
        inst.setUpstreamModel(rv.upstreamModel());
        inst.setVendor(rv.vendor());
        inst.setStatus(rv.instanceStatus());
        inst.setMeta(rv.instanceMeta());
        return new Candidate(rv.vendor(), inst, rv.upstreamModel());
    }

    /** 替换 JSON 请求体中的 "model" 字段为上游模型名称。 */
    private static byte[] substituteModel(byte[] rawBody, String upstreamModel, String srcModel) {
        if (upstreamModel == null || upstreamModel.equals(srcModel)) return rawBody;
        try {
            JsonObject json = new JsonObject(new String(rawBody));
            json.put("model", upstreamModel);
            return json.encode().getBytes();
        } catch (Exception e) {
            return rawBody;
        }
    }

    private static Comparator<RoutedVendor> buildSorter(AppConfig config) {
        List<String> layerOrder = config.getRelay().getLayerOrder();
        return new ByPref()
            .thenComparing(new ByInstanceLayer(layerOrder))
            .thenComparing(new ByVendorLayer(layerOrder))
            .thenComparing(new RawStatusLast())
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
