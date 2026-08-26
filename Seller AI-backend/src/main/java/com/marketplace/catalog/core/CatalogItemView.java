package com.marketplace.catalog.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Device-type-agnostic view of a catalog item. This is the shape the agent's
 * tool layer sees for every device type, so one prompt handles all of them.
 *
 * {@code specs} holds the device's typed columns flattened to a map;
 * {@code extraSpecs} holds whitelisted free-form keys.
 */
public record CatalogItemView(
        UUID id,
        DeviceType deviceType,
        String brand,
        String subBrand,
        String segment,
        String priceTier,
        String modelName,
        BigDecimal basePrice,
        BigDecimal maxDiscountPct,
        int stockQty,
        boolean inStock,
        Map<String, Object> specs,
        Map<String, Object> extraSpecs
) {}
