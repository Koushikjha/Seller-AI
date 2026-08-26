package com.marketplace.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny helpers so the tool definitions below read like a spec, not like JSON assembly. */
final class ToolSchemas {

    private ToolSchemas() {}

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    static Map<String, Object> str(String description) {
        return prop("string", description, null);
    }

    static Map<String, Object> str(String description, List<String> enumValues) {
        return prop("string", description, enumValues);
    }

    static Map<String, Object> number(String description) {
        return prop("number", description, null);
    }

    static Map<String, Object> integer(String description) {
        return prop("integer", description, null);
    }

    static Map<String, Object> bool(String description) {
        return prop("boolean", description, null);
    }

    static Map<String, Object> array(String itemType, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "array");
        p.put("items", Map.of("type", itemType));
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> prop(String type, String description, List<String> enumValues) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        if (enumValues != null && !enumValues.isEmpty()) {
            p.put("enum", enumValues);
        }
        return p;
    }

    @SafeVarargs
    static Map<String, Object> props(Map.Entry<String, Map<String, Object>>... entries) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (var e : entries) {
            m.put(e.getKey(), e.getValue());
        }
        return m;
    }

    static Map.Entry<String, Map<String, Object>> p(String name, Map<String, Object> schema) {
        return Map.entry(name, schema);
    }
}
