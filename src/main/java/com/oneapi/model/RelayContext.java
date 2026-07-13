package com.oneapi.model;

import com.oneapi.core.RouterService.RoutedVendor;

import java.util.ArrayList;
import java.util.List;

/**
 * 中继上下文 — 在过滤器链和协调器中传递的请求状态。
 * <p>
 * 生命周期：
 * - 阶段 1（请求解析）：由 RelayCoordinator 创建，填入 requestedModel、rawBody
 * - 阶段 2（模型解析）：由 VirtualModelLookup 等 filter 填入 matchRule、routingModelName、modelNames
 * - 阶段 3（候选过滤）：由 RouterService 填入 candidates，各 filter 逐步移除不符合条件的候选
 * - 阶段 4（排序）：由 sorter 对 candidates 排序
 * - 阶段 5（执行）：由 RelayCoordinator 选第一个候选执行中继
 * <p>
 * 如果 markError() 被调用，协调器会跳过阶段 3-5，直接返回错误响应。
 * <p>
 * Accessor 风格为 record 式（{@code xxx()}），而非 JavaBean 式（{@code getXxx()}），
 * 与 {@link RelayRequest} 等其他值对象保持一致。
 */
public class RelayContext {
    // 阶段2：模型解析
    private String requestedModel;
    private String routingModelName;
    private MatchRule matchRule;
    private boolean matchedPhysical;
    private String capabilityRequired;
    private boolean reasoning;

    // 阶段3：候选列表
    private List<RoutedVendor> candidates;
    
    /** ModelsMatch 的逻辑模型名列表。非空时 RelayCoordinator 以此为准，忽略 routingModelName。 */
    private List<String> modelNames;
    
    /** Raw request body bytes, set by RelayCoordinator before stage2 filters run. */
    private byte[] rawBody;

    // 错误状态
    private RelayError relayError;
    /**
     * 错误的人类可读消息。与 {@link #relayError} 同时设置。
     * <p>
     * 典型用法：{@code relayError.toString()} 含类型信息（如 "upstream 429"、"model not found"），
     * 而 errorMessage 可附带上游返回的 detail 信息，便于排查。
     * 调用方按需要选用任一字段。
     */
    private String errorMessage;

    /** filter 链操作日志：每个 filter 的移除记录 */
    private final List<FilterAction> filterLog = new ArrayList<>();

    public RelayContext(String requestedModel) {
        this.requestedModel = requestedModel;
    }

    public record FilterAction(String filterName, int beforeCount, int afterCount,
                                List<Integer> removedInstanceIds, String reason) {}

    public void addFilterAction(String filterName, int before, int after,
                                 List<Integer> removedIds, String reason) {
        filterLog.add(new FilterAction(filterName, before, after, removedIds, reason));
    }

    public List<FilterAction> filterLog() { return filterLog; }

    // --- 错误方法 ---

    public boolean hasError() { return relayError != null; }
    public RelayError error() { return relayError; }
    public String errorMessage() { return errorMessage; }

    /**
     * Mark context as failed with a typed error and a message.
     */
    public void markError(RelayError err, String msg) {
        this.relayError = err;
        this.errorMessage = msg;
    }

    // --- 阶段2 访问器 ---

    public String requestedModel() { return requestedModel; }
    public void setRequestedModel(String v) { this.requestedModel = v; }

    public String routingModelName() { return routingModelName; }
    public void setRoutingModelName(String v) { this.routingModelName = v; }

    public MatchRule matchRule() { return matchRule; }
    public void setMatchRule(MatchRule v) { this.matchRule = v; }

    public boolean matchedPhysical() { return matchedPhysical; }
    public void setMatchedPhysical(boolean v) { this.matchedPhysical = v; }

    public String capabilityRequired() { return capabilityRequired; }
    public void setCapabilityRequired(String v) { this.capabilityRequired = v; }

    public boolean reasoning() { return reasoning; }
    public void setReasoning(boolean v) { this.reasoning = v; }

    // --- 阶段3 / 4 访问器 ---

    public List<RoutedVendor> candidates() { return candidates; }
    public void setCandidates(List<RoutedVendor> v) { candidates = v; }
    
    public List<String> modelNames() { return modelNames; }
    public void setModelNames(List<String> v) { this.modelNames = v; }
    
    public byte[] rawBody() { return rawBody; }
    public void setRawBody(byte[] v) { this.rawBody = v; }
}
