package com.oneapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class FilterUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从 meta JSON 中解析实例标签。
     */
    public static List<String> parseTags(String meta) {
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
