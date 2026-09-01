package com.marketplace.laptop.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Merchant changes how far the agent may go on a given laptop. */
public record DiscountLimitUpdateRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal maxDiscountPct
) {}