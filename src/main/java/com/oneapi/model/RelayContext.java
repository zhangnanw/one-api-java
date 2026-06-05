package com.oneapi.model;

import java.util.*;

/**
 * 在过滤器链中传递的阶段上下文。
 */
public class RelayContext {
    // 阶段2：模型解析
    private String requestedModel;
    private String upstreamModel;
    private MatchRule matchRule;
    private boolean matchedPhysical;
    private String capabilityRequired;
    private boolean reasoning;
    
    // 阶段3：候选列表
    private List<Object> candidates;  // 占位符，后续类型化
    
    // 阶段4：排序
    private List<Object> sortedCandidates;
    
    // 错误状态
    private RelayError relayError;
    private String errorMessage;
    
    public RelayContext(String requestedModel) {
        this.requestedModel = requestedModel;
    }
    
    // 错误方法
    public boolean hasError() { return relayError != null; }
    public RelayError error() { return relayError; }
    public String errorMessage() { return errorMessage; }
    public void markError(RelayError err, String msg) {
        this.relayError = err;
        this.errorMessage = msg;
    }
    
    // getter/setter
    public String requestedModel() { return requestedModel; }
    public void setRequestedModel(String v) { requestedModel = v; }
    
    public String upstreamModel() { return upstreamModel; }
    public void setUpstreamModel(String v) { upstreamModel = v; }
    
    public MatchRule matchRule() { return matchRule; }
    public void setMatchRule(MatchRule v) { matchRule = v; }
    
    public boolean matchedPhysical() { return matchedPhysical; }
    public void setMatchedPhysical(boolean v) { matchedPhysical = v; }
    
    public String capabilityRequired() { return capabilityRequired; }
    public void setCapabilityRequired(String v) { capabilityRequired = v; }
    
    public boolean reasoning() { return reasoning; }
    public void setReasoning(boolean v) { reasoning = v; }
    
    @SuppressWarnings("unchecked")
    public <T> List<T> candidates() { return (List<T>) (Object) candidates; }
    public void setCandidates(List<?> v) { candidates = (List<Object>) v; }
    
    @SuppressWarnings("unchecked")
    public <T> List<T> sortedCandidates() { return (List<T>) (Object) sortedCandidates; }
    public void setSortedCandidates(List<?> v) { sortedCandidates = (List<Object>) v; }
}
