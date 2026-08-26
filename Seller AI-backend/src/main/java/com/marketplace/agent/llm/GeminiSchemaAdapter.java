package com.marketplace.agent.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gemini accepts an OpenAPI subset of JSON Schema, not the full thing.
 * Most notably it rejects {@code additionalProperties}, which our tool
 * definitions emit — so it gets stripped here rather than being left out
 * of the manifest, where it is legitimate and useful for other providers.
 */
final class GeminiSchemaAdapter {

    private static final List<String> UNSUPPORTED = List.of(
            "additionalProperties", "$schema", "definitions", "$defs", "examples");

    private GeminiSchemaAdapter() {}

    @SuppressWarnings("unchecked")
    static Object sanitize(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                String key = String.valueOf(k);
                if (UNSUPPORTED.contains(key)) return;
                // Gemini rejects an empty required array on some model versions.
                if ("required".equals(key) && v instanceof List<?> list && list.isEmpty()) return;
                out.put(key, sanitize(v));
            });
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(item -> out.add(sanitize(item)));
            return out;
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> sanitizeSchema(Map<String, Object> schema) {
        return (Map<String, Object>) sanitize(schema);
    }
}
