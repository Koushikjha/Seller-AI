package com.marketplace.catalog.dto;

import com.marketplace.catalog.entity.Brand;

import java.util.UUID;

public record BrandDto(
        UUID id,
        String name,
        String countryOfOrigin,
        String supportTier,
        Integer defaultWarrantyMonths,
        String brandPositioning
) {
    public static BrandDto from(Brand b) {
        return new BrandDto(b.getId(), b.getName(), b.getCountryOfOrigin(), b.getSupportTier(),
                b.getDefaultWarrantyMonths(), b.getBrandPositioning());
    }
}
