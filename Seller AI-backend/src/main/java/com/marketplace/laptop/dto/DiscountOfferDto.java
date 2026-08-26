package com.marketplace.laptop.dto;

import com.marketplace.laptop.entity.DiscountOffer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record DiscountOfferDto(
        UUID offerId,
        UUID laptopId,
        String modelName,
        BigDecimal requestedPct,
        BigDecimal approvedPct,
        BigDecimal listPrice,
        BigDecimal priceAfterDiscount,
        BigDecimal savings,
        int negotiationRounds,
        Instant expiresAt,
        boolean redeemed,
        String reason
) {
    public static DiscountOfferDto from(DiscountOffer o, String reason) {
        BigDecimal list = o.getLaptop().getBasePrice();
        BigDecimal finalPrice = applyPct(list, o.getApprovedPct());
        return new DiscountOfferDto(
                o.getId(), o.getLaptop().getId(), o.getLaptop().getModelName(),
                o.getRequestedPct(), o.getApprovedPct(),
                list, finalPrice, list.subtract(finalPrice),
                o.getNegotiationRounds(), o.getExpiresAt(), o.isRedeemed(), reason);
    }

    public static BigDecimal applyPct(BigDecimal price, BigDecimal pct) {
        BigDecimal p = pct == null ? BigDecimal.ZERO : pct;
        return price.subtract(price.multiply(p).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
