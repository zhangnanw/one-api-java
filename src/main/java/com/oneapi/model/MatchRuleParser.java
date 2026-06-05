package com.oneapi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/**
 * 将 VirtualModel.match JSON 解析为类型化的 MatchRule。
 * 一次解析——所有消费者读取同一对象。
 */
public class MatchRuleParser {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    /**
     * 解析 match JSON 字符串。
     * null、空字符串或 "{}" → AllMatch（匹配所有）
     */
    @SuppressWarnings("unchecked")
    public static MatchRule parse(String matchJson) {
        if (matchJson == null || matchJson.isEmpty() || "{}".equals(matchJson.trim())) {
            return new MatchRule.AllMatch();
        }
        try {
            Map<String, Object> m = mapper.readValue(matchJson, Map.class);
            
            if (m.containsKey("model_name")) {
                return new MatchRule.NameMatch(m.get("model_name").toString());
            }
            if (m.containsKey("capability")) {
                return new MatchRule.CapabilityMatch(m.get("capability").toString());
            }
            if (m.containsKey("layer")) {
                return new MatchRule.LayerMatch(m.get("layer").toString());
            }
            
            Set<String> all = parseTags(m, "all");
            Set<String> any = parseTags(m, "any");
            if (!all.isEmpty() || !any.isEmpty()) {
                return new MatchRule.TagMatch(all, any);
            }
            
            return new MatchRule.AllMatch();
        } catch (Exception e) {
            return new MatchRule.AllMatch();
        }
    }
    
    @SuppressWarnings("unchecked")
    private static Set<String> parseTags(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val instanceof List<?> list) {
            Set<String> tags = new HashSet<>();
            for (Object t : list) tags.add(t.toString());
            return Collections.unmodifiableSet(tags);
        }
        return Set.of();
    }
}
