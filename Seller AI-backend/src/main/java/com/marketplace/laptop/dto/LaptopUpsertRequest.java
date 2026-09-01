package com.marketplace.laptop.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Merchant-facing create/update payload. */
public record LaptopUpsertRequest(
        @NotNull UUID subBrandId,
        @NotNull UUID cpuId,
        UUID gpuId,

        @NotBlank @Size(max = 120) String modelName,
        @NotNull @DecimalMin("0.0") BigDecimal basePrice,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal maxDiscountPct,
        @NotNull @Min(0) Integer stockQty,

        @NotNull @Min(1) Integer ramGb,
        String ramType,
        @NotNull @Min(1) Integer storageGb,
        String storageType,

        BigDecimal displayInches,
        String displayType,
        Integer refreshRateHz,
        Boolean touchscreen,

        BigDecimal weightKg,
        Integer batteryHours,
        String os,
        Integer releaseYear,

        Map<String, Object> extraSpecs,
        List<String> images
) {}