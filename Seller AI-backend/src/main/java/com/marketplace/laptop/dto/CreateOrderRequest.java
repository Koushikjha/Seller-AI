package com.marketplace.laptop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID laptopId,
        @NotBlank String identityKey,
        UUID discountOfferId
) {}
