package com.marketplace.catalog.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rejects any extraSpecs key outside the device type's vocabulary, and any
 * value whose JSON type does not match the declared type. This is the check
 * that keeps merchant input and agent vocabulary in lockstep.
 */
public final class SpecKeyValidator {

    private SpecKeyValidator() {}

    public static void validate(Map<String, Object> extraSpecs, List<? extends SpecKey> vocabulary) {
        if (extraSpecs == null || extraSpecs.isEmpty()) {
            return;
        }
        Map<String, SpecKey> allowed = vocabulary.stream()
                .collect(Collectors.toMap(SpecKey::key, k -> k));

        Set<String> unknown = extraSpecs.keySet().stream()
                .filter(k -> !allowed.containsKey(k))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown extraSpecs key(s): " + unknown + ". Allowed keys: " + allowed.keySet());
        }

        extraSpecs.forEach((key, value) -> {
            if (value == null) return;
            SpecKey spec = allowed.get(key);
            if (!typeMatches(spec.type(), value)) {
                throw new IllegalArgumentException(
                        "extraSpecs." + key + " must be of type " + spec.type()
                                + " but got " + value.getClass().getSimpleName());
            }
        });
    }

    private static boolean typeMatches(String declared, Object value) {
        return switch (declared) {
            case "string"   -> value instanceof String;
            case "boolean"  -> value instanceof Boolean;
            case "number"   -> value instanceof Number;
            case "string[]" -> value instanceof List<?> list
                    && list.stream().allMatch(e -> e instanceof String);
            default -> true;
        };
    }
}
