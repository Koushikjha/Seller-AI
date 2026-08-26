package com.marketplace.laptop.dto;

import com.marketplace.laptop.entity.MarketplaceOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderDto(
        UUID orderId,
        UUID laptopId,
        String modelName,
        String identityKey,
        BigDecimal listPrice,
        BigDecimal discountPct,
        BigDecimal finalPrice,
        UUID discountOfferId,
        String status,
        String paymentRef,
        String paymentLink,
        Instant createdAt
) {
    public static OrderDto from(MarketplaceOrder o) {
        return new OrderDto(
                o.getId(), o.getLaptop().getId(), o.getLaptop().getModelName(), o.getIdentityKey(),
                o.getListPrice(), o.getDiscountPct(), o.getFinalPrice(),
                o.getDiscountOffer() == null ? null : o.getDiscountOffer().getId(),
                o.getStatus().name(), o.getPaymentRef(), o.getPaymentLink(), o.getCreatedAt());
    }
}
