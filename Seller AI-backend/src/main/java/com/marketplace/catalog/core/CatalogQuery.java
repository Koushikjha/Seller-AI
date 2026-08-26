package com.marketplace.catalog.core;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cross-device search criteria. Anything a provider does not understand it
 * simply ignores; unknown filters never silently widen the result set.
 *
 * {@code inStockOnly} defaults to true on purpose: the agent must not be able
 * to pitch something the shop cannot sell.
 */
public record CatalogQuery(
        DeviceType deviceType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minRamGb,
        Integer minStorageGb,
        String brand,
        String segment,
        String priceTier,
        String cpuBenchmarkTier,
        boolean inStockOnly,
        Integer limit,
        Map<String, String> deviceSpecific
) {
    public int effectiveLimit() {
        return limit == null || limit <= 0 ? 10 : Math.min(limit, 25);
    }
}
