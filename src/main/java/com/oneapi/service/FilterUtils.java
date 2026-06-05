package com.oneapi.service;

import com.oneapi.model.Instance;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Filter chain for instance selection.
 * Each Filter.Accept(instance) determines if an instance passes.
 */
public class FilterUtils {
    private static final Logger log = LoggerFactory.getLogger(FilterUtils.class);
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

    /**
     * Parse instance meta JSON into a Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMeta(String meta) {
        if (meta == null || meta.isEmpty()) return Map.of();
        try {
            return mapper.readValue(meta, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Full-text tag matching: ALL and ANY conditions.
     * Used by virtual model match rules.
     */
    public static boolean matchAll(List<String> instanceTags, List<String> required) {
        if (required == null || required.isEmpty()) return true;
        Set<String> tagSet = new HashSet<>(instanceTags);
        for (String r : required) {
            if (!tagSet.contains(r)) return false;
        }
        return true;
    }

    public static boolean matchAny(List<String> instanceTags, List<String> any) {
        if (any == null || any.isEmpty()) return true;
        Set<String> tagSet = new HashSet<>(instanceTags);
        return any.stream().anyMatch(tagSet::contains);
    }

    // --- Layer & Pref filtering (RoutedVendor-level pipelines) ---

    /**
     * Layer filter — keep only instances whose meta.layer is in the allow list.
     * @param list       candidate RoutedVendor list
     * @param layerAllow comma-separated layer names, e.g. "payg,free"
     * @return filtered list
     */
    public static List<RouterService.RoutedVendor> applyLayer(
            List<RouterService.RoutedVendor> list, String layerAllow) {
        if (layerAllow == null || layerAllow.isEmpty()) return list;
        Set<String> allowed = new HashSet<>();
        for (String s : layerAllow.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) allowed.add(t);
        }
        if (allowed.isEmpty()) return list;
        return list.stream()
            .filter(rv -> {
                Map<String, Object> m = parseMeta(rv.instanceMeta());
                String layer = m.getOrDefault("layer", "").toString();
                return !layer.isEmpty() && allowed.contains(layer);
            })
            .toList();
    }

    /**
     * Max-pref filter — keep only instances whose pref ≤ maxPref.
     * Missing pref defaults to 0 (matches Go: zero-value always passes).
     * @param list    candidate RoutedVendor list
     * @param maxPref upper bound (inclusive)
     * @return filtered list
     */
    public static List<RouterService.RoutedVendor> applyMaxPref(
            List<RouterService.RoutedVendor> list, int maxPref) {
        return list.stream()
            .filter(rv -> {
                Map<String, Object> m = parseMeta(rv.instanceMeta());
                Object pref = m.get("pref");
                double prefVal = pref instanceof Number n ? n.doubleValue() : 0;
                return prefVal <= maxPref;
            })
            .toList();
    }

    // --- Filter interface ---

    public interface Filter {
        boolean accept(Instance inst);
    }

    /**
     * Tag-based filter: instance must have ALL specified tags.
     */
    public static Filter tagAllFilter(List<String> tags) {
        return inst -> matchAll(parseTags(inst.getMeta()), tags);
    }

    /**
     * Tag-based filter: instance must have ANY specified tag.
     */
    public static Filter tagAnyFilter(List<String> tags) {
        return inst -> matchAny(parseTags(inst.getMeta()), tags);
    }

    /**
     * Model name filter: exact match on instance model_name.
     */
    public static Filter modelNameFilter(String modelName) {
        return inst -> modelName != null && modelName.equals(inst.getModelName());
    }

    /**
     * Build filter chain from VirtualModel match JSON.
     * Match format: {"all":["tag1"],"any":["tag2"],"model_name":"gpt-4","layer":"free"}
     */
    @SuppressWarnings("unchecked")
    public static List<Filter> fromMatchJson(String matchJson) {
        List<Filter> filters = new ArrayList<>();
        if (matchJson == null || matchJson.isEmpty() || "{}".equals(matchJson)) {
            return filters;
        }
        try {
            Map<String, Object> match = mapper.readValue(matchJson, Map.class);

            if (match.containsKey("all")) {
                Object all = match.get("all");
                if (all instanceof List) {
                    List<String> tags = ((List<Object>) all).stream()
                        .map(Object::toString).toList();
                    if (!tags.isEmpty()) {
                        filters.add(tagAllFilter(tags));
                    }
                }
            }

            if (match.containsKey("any")) {
                Object any = match.get("any");
                if (any instanceof List) {
                    List<String> tags = ((List<Object>) any).stream()
                        .map(Object::toString).toList();
                    if (!tags.isEmpty()) {
                        filters.add(tagAnyFilter(tags));
                    }
                }
            }

            if (match.containsKey("model_name")) {
                String modelName = match.get("model_name").toString();
                filters.add(modelNameFilter(modelName));
            }
        } catch (Exception e) {
            log.warn("Failed to parse match JSON: {}", e.getMessage());
        }
        return filters;
    }

    /**
     * Parse the "layer" field from virtual model match JSON.
     * @param matchJson virtual model match JSON
     * @return comma-separated layer allow list, or null if not specified
     */
    public static String parseMatchLayer(String matchJson) {
        if (matchJson == null || matchJson.isEmpty()) return null;
        try {
            var node = mapper.readTree(matchJson);
            if (node.has("layer")) {
                return node.get("layer").asText();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Parse the "max_pref" field from virtual model match JSON.
     * @param matchJson virtual model match JSON
     * @param fallback  default value if not specified
     * @return parsed max_pref value, or fallback
     */
    public static int parseMatchMaxPref(String matchJson, int fallback) {
        if (matchJson == null || matchJson.isEmpty()) return fallback;
        try {
            var node = mapper.readTree(matchJson);
            if (node.has("max_pref")) {
                return node.get("max_pref").asInt();
            }
        } catch (Exception e) {
            // ignore
        }
        return fallback;
    }
}
