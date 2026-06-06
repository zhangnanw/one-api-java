package com.oneapi.model;

/**
 * 用于虚拟模型匹配规则的类型密封层级。
 * 替代不透明的 JSON 匹配字符串。
 */
public sealed interface MatchRule 
    permits MatchRule.AllMatch, MatchRule.NameMatch, 
            MatchRule.TagMatch, MatchRule.CapabilityMatch, 
            MatchRule.LayerMatch {
    
    /** 匹配所有实例（无过滤条件） */
    record AllMatch() implements MatchRule {}
    
    /** 按 model_name 匹配（例如 "deepseek-v3"） */
    record NameMatch(String modelName) implements MatchRule {}
    
    /** 按标签存在性匹配（例如 "capability:reasoning"） */
    record TagMatch(java.util.Set<String> allTags, java.util.Set<String> anyTags) implements MatchRule {}
    
    /** 按能力字符串匹配（例如 "vision"） */
    record CapabilityMatch(String capability) implements MatchRule {}
    
    /** 按层级匹配（例如 "payg"） */
    record LayerMatch(String layer) implements MatchRule {}
}
