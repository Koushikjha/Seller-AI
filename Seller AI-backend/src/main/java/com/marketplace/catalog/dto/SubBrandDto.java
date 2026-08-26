package com.marketplace.catalog.dto;

import com.marketplace.catalog.entity.SubBrand;

import java.util.UUID;

public record SubBrandDto(
        UUID id,
        UUID brandId,
        String brandName,
        String name,
        String segment,
        String priceTier,
        String buildQualityTier,
        String targetPersona
) {
    public static SubBrandDto from(SubBrand sb) {
        return new SubBrandDto(sb.getId(), sb.getBrand().getId(), sb.getBrand().getName(), sb.getName(),
                sb.getSegment(), sb.getPriceTier(), sb.getBuildQualityTier(), sb.getTargetPersona());
    }
}
