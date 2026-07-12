package com.oneapi.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 将 VirtualModel.match JSON 解析为类型化的 MatchRule。
 * 一次解析——所有消费者读取同一对象。
 */
@Slf4j
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
            
            if (m.containsKey("models")) {
                return parseModelsMatch(m);
            }
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
        } catch (IllegalArgumentException e) {
            throw e; // 必须在 catch(Exception) 之前，否则校验异常被吞
        } catch (Exception e) {
            log.warn("Failed to parse match JSON: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to parse match JSON: " + e.getMessage(), e);
        }
    }
    
    private static Set<String> parseTags(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val instanceof List<?> list) {
            Set<String> tags = new HashSet<>();
            for (Object t : list) tags.add(t.toString());
            return Collections.unmodifiableSet(tags);
        }
        return Set.of();
    }
    
    private static MatchRule.ModelsMatch parseModelsMatch(Map<String, Object> m) {
        // 互斥检测：models 不与旧字段共存
        if (m.containsKey("model_name") || m.containsKey("capability")
            || m.containsKey("layer") || m.containsKey("all") || m.containsKey("any")) {
            throw new IllegalArgumentException(
                "models 字段不能与 model_name/capability/layer/all/any 共存");
        }
        Object modelsObj = m.get("models");
        if (!(modelsObj instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("models 必须是非空列表");
        }
        List<String> names = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String s) || s.isEmpty()) {
                throw new IllegalArgumentException(
                    "models 列表中每个元素必须是非空字符串，收到: " + item);
            }
            names.add(s);
        }
        return new MatchRule.ModelsMatch(List.copyOf(names));
    }
}
