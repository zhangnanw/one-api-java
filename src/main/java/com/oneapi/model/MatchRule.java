package com.oneapi.model;

/**
 * Sealed hierarchy for virtual model match rules.
 * Replaces the opaque JSON match string.
 */
public sealed interface MatchRule 
    permits MatchRule.AllMatch, MatchRule.NameMatch, 
            MatchRule.TagMatch, MatchRule.CapabilityMatch, 
            MatchRule.LayerMatch {
    
    /** Match all instances (no filter) */
    record AllMatch() implements MatchRule {}
    
    /** Match by model_name (e.g. "deepseek-v3") */
    record NameMatch(String modelName) implements MatchRule {}
    
    /** Match by tag presence (e.g. "capability:reasoning") */
    record TagMatch(java.util.Set<String> all, java.util.Set<String> any) implements MatchRule {}
    
    /** Match by capability string (e.g. "vision") */
    record CapabilityMatch(String capability) implements MatchRule {}
    
    /** Match by layer (e.g. "payg") */
    record LayerMatch(String layer) implements MatchRule {}
}
