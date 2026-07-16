package com.oneapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 meta JSON 中解析实例标签。
 * <p>
 * 现在作为 Spring 组件管理，注入统一的 {@link ObjectMapper}。
 */
@Component
public class FilterUtils {

    private final ObjectMapper mapper;

    public FilterUtils(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 从 meta JSON 中解析实例标签。
     */
    public List<String> parseTags(String meta) {
        if (meta == null || meta.isEmpty()) return List.of();
        try {
            var node = mapper.readTree(meta);
            var tagsNode = node.get("tags");
            if (tagsNode != null && tagsNode.isArray()) {
                List<String> tags = new ArrayList<>();
                tagsNode.forEach(t -> tags.add(t.asText()));
                return tags;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return List.of();
    }
}
