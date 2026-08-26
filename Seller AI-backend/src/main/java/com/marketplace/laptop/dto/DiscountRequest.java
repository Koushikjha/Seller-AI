package com.marketplace.laptop.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record DiscountRequest(
        @NotNull UUID laptopId,
        @NotBlank String identityKey,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal requestedPct,
        @Min(0) @Max(20) Integer negotiationRounds
) {
    public int rounds() {
        return negotiationRounds == null ? 0 : negotiationRounds;
    }
}
