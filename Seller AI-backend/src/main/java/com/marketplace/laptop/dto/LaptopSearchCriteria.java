package com.marketplace.laptop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Every filter the laptop search tool accepts. Deliberately a flat record:
 * it maps 1:1 onto the JSON-schema the agent's tool definition exposes.
 */
@Schema(description = "Laptop search filters. Unset fields are ignored.")
public record LaptopSearchCriteria(
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minRam,
        Integer minStorage,
        String storageType,
        String os,
        Integer refreshRateMin,
        BigDecimal maxWeightKg,
        Integer minBatteryHours,
        Boolean touchscreen,
        String displayType,
        String brand,
        String subBrand,
        String segment,
        String priceTier,
        String cpuBenchmarkTier,
        String gpuBrand,
        String gpuBenchmarkTier,
        Boolean discreteGpuRequired,
        Integer minVramGb,
        String modelNameContains,
        boolean inStockOnly,
        Integer limit,
        String sort
) {
    public int effectiveLimit() {
        return limit == null || limit <= 0 ? 10 : Math.min(limit, 25);
    }
}
