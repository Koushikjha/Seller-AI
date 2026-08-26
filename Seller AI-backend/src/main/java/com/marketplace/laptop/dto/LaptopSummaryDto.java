package com.marketplace.laptop.dto;

import com.marketplace.laptop.entity.Laptop;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a search result looks like to the agent — and to a product card.
 *
 * Flat and small on purpose. The full LaptopDto carries nested brand, cpu, gpu
 * and sub_brand objects plus extraSpecs; ten of those is 8-10K tokens in one
 * tool result, which blows a free-tier per-minute token cap on its own. This is
 * roughly a fifth of the size and contains everything needed to decide which
 * machines are worth presenting. The agent calls get_laptop_details for the two
 * or three it actually pitches.
 *
 * Note what is deliberately absent: maxDiscountPct. The merchant's ceiling has
 * no business being in a list the agent scans — it can only leak. Negotiability
 * is a boolean here; the actual figure comes from request_discount and nowhere
 * else.
 */
public record LaptopSummaryDto(
        UUID id,
        String brand,
        String subBrand,
        String segment,
        String modelName,
        BigDecimal price,
        int stockQty,
        boolean negotiable,
        String cpu,
        String cpuTier,
        String gpu,
        Integer gpuVramGb,
        int ramGb,
        int storageGb,
        String storageType,
        BigDecimal displayInches,
        String displayType,
        Integer refreshRateHz,
        BigDecimal weightKg,
        Integer batteryHours,
        String os
) {
    public static LaptopSummaryDto from(Laptop l) {
        return new LaptopSummaryDto(
                l.getId(),
                l.getSubBrand().getBrand().getName(),
                l.getSubBrand().getName(),
                l.getSubBrand().getSegment(),
                l.getModelName(),
                l.getBasePrice(),
                l.getStockQty(),
                l.getMaxDiscountPct() != null && l.getMaxDiscountPct().compareTo(BigDecimal.ZERO) > 0,
                l.getCpu().getName(),
                l.getCpu().getBenchmarkTier(),
                l.getGpu() == null ? "Integrated" : l.getGpu().getName(),
                l.getGpu() == null ? null : l.getGpu().getVramGb(),
                l.getRamGb(),
                l.getStorageGb(),
                l.getStorageType(),
                l.getDisplayInches(),
                l.getDisplayType(),
                l.getRefreshRateHz(),
                l.getWeightKg(),
                l.getBatteryHours(),
                l.getOs());
    }
}