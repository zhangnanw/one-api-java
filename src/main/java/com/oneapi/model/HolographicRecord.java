package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.core.RouterService.RoutedVendor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 全息调试记录 — Builder 模式逐阶段填充。
 * <p>
 * 记录一次中继请求的完整生命周期数据，序列化为 JSON 存入 holographic_logs。
 * 通过 {@link RelayCoordinator} 在各阶段节点调用相应的填充方法。
 */
@Slf4j
public class HolographicRecord {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String requestId;
    private long timestampMs;
    private long startMs;

    // ── 阶段1：请求入口 ──
    private String clientIp;
    private String requestedModel;
    private boolean stream;
    private int bodySize;
    private String bodyPreview;
    private int messagesCount;
    private int maxTokens;
    private double temperature;
    private String authType;
    private String fullRequestBody;

    // ── 阶段2：模型解析 ──
    private String matchRuleType;
    private Object matchRuleDetail;
    private List<String> routingModelNames;
    private String capabilityRequired;
    private List<Map<String, String>> filterChain;

    // ── 阶段3：候选列表 ──
    private int totalCandidates;
    private List<Map<String, Object>> candidatesBefore;
    private List<Map<String, Object>> filterResults;
    private List<Map<String, Object>> candidatesAfter;
    private List<Map<String, Object>> sortOrder;

    // ── 阶段5：尝试记录 ──
    private final List<AttemptRecord> attempts;

    // ── 最终结果 ──
    private String finalStatus;
    private int finalHttpCode;
    private long totalLatencyMs;
    private int totalTokens;
    private String errorStage;
    private String errorDetail;
    private int retryCount;
    private String finalVendor;
    private int finalInstanceId;
    private String responseBody;

    private HolographicRecord(String requestId) {
        this.requestId = requestId;
        this.attempts = new ArrayList<>();
        this.filterChain = new ArrayList<>();
        this.candidatesBefore = new ArrayList<>();
        this.filterResults = new ArrayList<>();
        this.candidatesAfter = new ArrayList<>();
        this.sortOrder = new ArrayList<>();
    }

    // ── Builder ──

    public static Builder builder(String requestId) {
        return new Builder(requestId);
    }

    public static class Builder {
        private final HolographicRecord record;

        Builder(String requestId) {
            this.record = new HolographicRecord(requestId);
        }

        public Builder timestampMs(long v) { record.timestampMs = v; record.startMs = v; return this; }
        public Builder requestedModel(String v) { record.requestedModel = v; return this; }
        public Builder streaming(boolean v) { record.stream = v; return this; }
        public Builder bodySize(int v) { record.bodySize = v; return this; }
        public Builder clientIp(String v) { record.clientIp = v; return this; }

        /** 从原始请求体提取预览和元数据 */
        public Builder rawBody(byte[] rawBody) {
            if (rawBody == null || rawBody.length == 0) return this;
            try {
                String body = new String(rawBody, StandardCharsets.UTF_8);
                record.fullRequestBody = body;
                record.bodyPreview = body.length() > 200 ? body.substring(0, 200) + "..." : body;

                var json = new io.vertx.core.json.JsonObject(body);
                record.messagesCount = json.getJsonArray("messages", new io.vertx.core.json.JsonArray()).size();
                record.maxTokens = json.getInteger("max_tokens", 0);
                record.temperature = json.getDouble("temperature", 0.0);
            } catch (Exception e) {
                record.bodyPreview = "parse_error";
            }
            return this;
        }

        /** 构建初始记录（阶段1完成） */
        public HolographicRecord buildInitial() {
            record.authType = "Bearer";
            return record;
        }
    }

    // ── 阶段填充方法 ──

    /** 阶段2：路由信息 */
    public void routingInfo(RelayContext relayCtx) {
        this.routingModelNames = relayCtx.modelNames() != null
            ? relayCtx.modelNames()
            : (relayCtx.routingModelName() != null
                ? List.of(relayCtx.routingModelName())
                : List.of());

        this.capabilityRequired = relayCtx.capabilityRequired();

        MatchRule rule = relayCtx.matchRule();
        if (rule != null) {
            this.matchRuleType = rule.getClass().getSimpleName();
            if (rule instanceof MatchRule.ModelsMatch mm) {
                this.matchRuleDetail = mm.modelNames();
            } else if (rule instanceof MatchRule.NameMatch nm) {
                this.matchRuleDetail = nm.modelName();
            } else if (rule instanceof MatchRule.TagMatch tm) {
                var detail = new HashMap<String, Object>();
                detail.put("all", tm.allTags());
                detail.put("any", tm.anyTags());
                this.matchRuleDetail = detail;
            } else if (rule instanceof MatchRule.CapabilityMatch cm) {
                this.matchRuleDetail = cm.capability();
            } else if (rule instanceof MatchRule.LayerMatch lm) {
                this.matchRuleDetail = lm.layer();
            } else {
                this.matchRuleDetail = "all";
            }
        }

        // 记录 filter 链执行（简化版：记录 filter 类名）
        this.filterChain = new ArrayList<>();
        this.filterChain.add(Map.of("filter", "VirtualModelLookup", "action", "pass"));
        this.filterChain.add(Map.of("filter", "ActiveStatusFilter", "action", "pass"));
    }

    /** 阶段3：候选信息 */
    public void candidatesInfo(List<RoutedVendor> originalCandidates,
                                List<RelayContext.FilterAction> filterLog,
                                Collection<RoutedVendor> sorted) {
        this.totalCandidates = originalCandidates != null ? originalCandidates.size() : 0;

        if (originalCandidates != null) {
            this.candidatesBefore = originalCandidates.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vendor", c.vendor() != null ? c.vendor().getName() : "?");
                    m.put("instance_id", c.instanceId());
                    m.put("model", c.modelName());
                    m.put("upstream", c.upstreamModel());
                    m.put("tags", c.instanceTags());
                    m.put("status", c.instanceStatus());
                    parseMeta(c.instanceMeta(), m);
                    return m;
                })
                .collect(Collectors.toList());
        }

        if (filterLog != null && !filterLog.isEmpty()) {
            this.filterResults = filterLog.stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("filter", a.filterName());
                    m.put("before", a.beforeCount());
                    m.put("after", a.afterCount());
                    m.put("removed", a.removedInstanceIds());
                    m.put("reason", a.reason());
                    return m;
                })
                .collect(Collectors.toList());
        } else {
            this.filterResults = List.of();
        }

        this.candidatesAfter = sorted != null
            ? sorted.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vendor", c.vendor() != null ? c.vendor().getName() : "?");
                    m.put("instance_id", c.instanceId());
                    m.put("model", c.modelName());
                    parseMeta(c.instanceMeta(), m);
                    return m;
                })
                .collect(Collectors.toList())
            : List.of();

        if (sorted != null) {
            this.sortOrder = sorted.stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("vendor", c.vendor() != null ? c.vendor().getName() : "?");
                    m.put("instance_id", c.instanceId());
                    m.put("model", c.modelName());
                    return m;
                })
                .collect(Collectors.toList());
        }
    }

    /** 添加一次中继尝试 */
    public void addAttempt(AttemptRecord attempt) {
        this.attempts.add(attempt);
    }

    /** 从 instanceMeta JSON 提取 layer 和 pref */
    private static void parseMeta(String metaJson, Map<String, Object> target) {
        if (metaJson == null || metaJson.isEmpty()) return;
        try {
            var node = MAPPER.readTree(metaJson);
            if (node.has("layer")) target.put("layer", node.get("layer").asText());
            if (node.has("pref")) target.put("pref", node.get("pref").asDouble());
        } catch (Exception ignored) {
        }
    }

    /** 最终完成 */
    public void finish(String finalStatus, int finalHttpCode, long totalLatencyMs,
                        int totalTokens, String responseBody, String errorStage,
                        String errorDetail, int retryCount, String finalVendor,
                        int finalInstanceId) {
        this.finalStatus = finalStatus;
        this.finalHttpCode = finalHttpCode;
        this.totalLatencyMs = totalLatencyMs;
        this.totalTokens = totalTokens;
        this.responseBody = responseBody;
        this.errorStage = errorStage;
        this.errorDetail = errorDetail;
        this.retryCount = retryCount;
        this.finalVendor = finalVendor;
        this.finalInstanceId = finalInstanceId;
    }

    // ── 序列化 ──

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.warn("HolographicRecord toJson failed: {}", e.getMessage(), e);
            return "{}";
        }
    }

    // ── Jackson 序列化 getter（保持与原手动 LinkedHashMap 相同的嵌套结构） ──

    @JsonProperty("request")
    Map<String, Object> getRequest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("client_ip", clientIp);
        m.put("requested_model", requestedModel);
        m.put("stream", stream);
        m.put("body_size", bodySize);
        m.put("body_preview", bodyPreview);
        m.put("messages_count", messagesCount);
        m.put("max_tokens", maxTokens);
        m.put("temperature", temperature);
        m.put("auth_type", authType);
        m.put("full_body", fullRequestBody);
        return m;
    }

    @JsonProperty("routing")
    Map<String, Object> getRouting() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("match_rule_type", matchRuleType);
        m.put("match_rule_detail", matchRuleDetail);
        m.put("routing_model_names", routingModelNames);
        m.put("capability_required", capabilityRequired);
        m.put("filter_chain", filterChain);
        return m;
    }

    @JsonProperty("candidates")
    Map<String, Object> getCandidates() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", totalCandidates);
        m.put("before_filter", candidatesBefore);
        m.put("filter_results", filterResults);
        m.put("after_filter", candidatesAfter);
        m.put("sort_order", sortOrder);
        return m;
    }

    @JsonProperty("attempts")
    List<AttemptRecord> getAttempts() {
        for (int i = 0; i < attempts.size(); i++) {
            attempts.get(i).seqN = i + 1;
        }
        return attempts;
    }

    @JsonProperty("result")
    Map<String, Object> getResult() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("final_status", finalStatus);
        m.put("final_http_code", finalHttpCode);
        m.put("total_latency_ms", totalLatencyMs);
        m.put("total_tokens", totalTokens);
        m.put("error_stage", errorStage);
        m.put("error_detail", errorDetail);
        m.put("retry_count", retryCount);
        m.put("final_vendor", finalVendor);
        m.put("final_instance_id", finalInstanceId);
        m.put("response_body", responseBody);
        return m;
    }

    // ── Getters for DB ──

    public String requestId() { return requestId; }
    public long timestampMs() { return timestampMs; }
    public String requestedModel() { return requestedModel; }
    public String finalStatus() { return finalStatus; }
    public int finalHttpCode() { return finalHttpCode; }
    public long totalLatencyMs() { return totalLatencyMs; }
    public int totalTokens() { return totalTokens; }
    public long startMs() { return startMs; }
    public int attemptCount() { return attempts.size(); }

    // ── 内部类：尝试记录 ──

    public static class AttemptRecord {
        @JsonProperty("n") int seqN;
        @JsonProperty("vendor") public final String vendorName;
        @JsonProperty("instance_id") public final int instanceId;
        @JsonProperty("upstream_model") public final String upstreamModel;
        @JsonProperty("upstream_url") public final String upstreamUrl;
        @JsonProperty("http_status") public final int httpStatus;
        @JsonProperty("latency_ms") public final long latencyMs;
        @JsonProperty("ttfb_ms") public final long ttfbMs;
        @JsonProperty("error_type") public final String errorType;
        @JsonProperty("error_body") public final String errorBody;
        @JsonProperty("cooldown_triggered") public final boolean cooldownTriggered;
        public final int promptTokens;
        public final int completionTokens;

        private AttemptRecord(String vendorName, int instanceId, String upstreamModel,
                              String upstreamUrl, int httpStatus, long latencyMs, long ttfbMs,
                              String errorType, String errorBody, boolean cooldownTriggered,
                              int promptTokens, int completionTokens) {
            this.vendorName = vendorName;
            this.instanceId = instanceId;
            this.upstreamModel = upstreamModel;
            this.upstreamUrl = upstreamUrl;
            this.httpStatus = httpStatus;
            this.latencyMs = latencyMs;
            this.ttfbMs = ttfbMs;
            this.errorType = errorType;
            this.errorBody = errorBody;
            this.cooldownTriggered = cooldownTriggered;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }

        @JsonProperty("tokens")
        Map<String, Integer> getTokens() {
            Map<String, Integer> m = new LinkedHashMap<>();
            m.put("prompt", promptTokens);
            m.put("completion", completionTokens);
            return m;
        }

        public static AttemptRecord success(String vendorName, int instanceId,
                                             String upstreamModel, String upstreamUrl,
                                             int httpStatus, long latencyMs, long ttfbMs,
                                             int promptTokens, int completionTokens) {
            return new AttemptRecord(vendorName, instanceId, upstreamModel, upstreamUrl,
                httpStatus, latencyMs, ttfbMs, null, null, false,
                promptTokens, completionTokens);
        }

        public static AttemptRecord failure(String vendorName, int instanceId,
                                             String upstreamModel, String upstreamUrl,
                                             int httpStatus, long latencyMs,
                                             String errorType, String errorBody,
                                             boolean cooldownTriggered) {
            return new AttemptRecord(vendorName, instanceId, upstreamModel, upstreamUrl,
                httpStatus, latencyMs, 0, errorType, errorBody, cooldownTriggered, 0, 0);
        }
    }
}
