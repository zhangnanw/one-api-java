package com.oneapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public class FilterUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Parse instance tags from meta JSON.
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
            // ignore parse errors
        }
        return List.of();
    }
}
