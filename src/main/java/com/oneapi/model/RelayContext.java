package com.oneapi.model;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.List;

/**
 * 在过滤器链中传递的阶段上下文。
 * <p>
 * Stage context carried through the filter chain.
 * <p>
 * Accessor style is record-like ({@code xxx()}), not JavaBean ({@code getXxx()}),
 * to match {@link RelayRequest} and other value objects in the model package.
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
     * 典型用法：{@code relayError.toString()} 含类型信息（"upstream 429" / "model not found"），
     * 而 errorMessage 用于附加上下文（上游返回的 message 字段）。
     * 调用方可用其中任一字段，按可读性需要选用。
     */
    private String errorMessage;

    public RelayContext(String requestedModel) {
        this.requestedModel = requestedModel;
    }

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
