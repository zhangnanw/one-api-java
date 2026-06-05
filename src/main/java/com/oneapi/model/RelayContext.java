package com.oneapi.model;

import java.util.*;

/**
 * Stage context passed through the filter chain.
 */
public class RelayContext {
    // Stage 2: model resolution
    private String requestedModel;
    private String upstreamModel;
    private MatchRule matchRule;
    private boolean matchedPhysical;
    private String capabilityRequired;
    private boolean reasoning;
    
    // Stage 3: candidates
    private List<Object> candidates;  // placeholder, typed later
    
    // Stage 4: sorted
    private List<Object> sortedCandidates;
    
    // Error state
    private RelayError relayError;
    private String errorMessage;
    
    public RelayContext(String requestedModel) {
        this.requestedModel = requestedModel;
    }
    
    // Error methods
    public boolean hasError() { return relayError != null; }
    public RelayError error() { return relayError; }
    public String errorMessage() { return errorMessage; }
    public void markError(RelayError err, String msg) {
        this.relayError = err;
        this.errorMessage = msg;
    }
    
    // getters/setters
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
