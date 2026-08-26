package com.marketplace.laptop.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aligned spec rows: one row per attribute, one cell per laptop, in the same
 * order as {@code laptops}. {@code differing} flags rows where the values are
 * not all equal -- that is the list a salesperson actually argues from.
 */
public record CompareResponse(
        List<Column> laptops,
        List<Row> rows
) {
    public record Column(UUID id, String label) {}

    public record Row(String attribute, List<Object> values, boolean differing) {}

    public static Map<String, Object> emptyCell() {
        return Map.of();
    }
}
