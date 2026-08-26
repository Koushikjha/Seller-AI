package com.marketplace.laptop.dto;

import com.marketplace.catalog.dto.CpuDto;
import com.marketplace.catalog.dto.GpuDto;
import com.marketplace.catalog.dto.SubBrandDto;
import com.marketplace.laptop.entity.Laptop;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record LaptopDto(
        UUID id,
        String modelName,
        BigDecimal basePrice,
        BigDecimal maxDiscountPct,
        int stockQty,
        boolean inStock,
        SubBrandDto subBrand,
        CpuDto cpu,
        GpuDto gpu,
        int ramGb,
        String ramType,
        int storageGb,
        String storageType,
        BigDecimal displayInches,
        String displayType,
        Integer refreshRateHz,
        boolean touchscreen,
        BigDecimal weightKg,
        Integer batteryHours,
        String os,
        Integer releaseYear,
        Map<String, Object> extraSpecs
) {
    public static LaptopDto from(Laptop l) {
        return new LaptopDto(
                l.getId(), l.getModelName(), l.getBasePrice(), l.getMaxDiscountPct(),
                l.getStockQty(), l.isInStock(),
                SubBrandDto.from(l.getSubBrand()),
                CpuDto.from(l.getCpu()),
                l.getGpu() == null ? null : GpuDto.from(l.getGpu()),
                l.getRamGb(), l.getRamType(), l.getStorageGb(), l.getStorageType(),
                l.getDisplayInches(), l.getDisplayType(), l.getRefreshRateHz(), l.isTouchscreen(),
                l.getWeightKg(), l.getBatteryHours(), l.getOs(), l.getReleaseYear(),
                l.getExtraSpecs());
    }
}
