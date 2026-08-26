package com.marketplace.laptop.service;

import com.marketplace.config.MarketplaceProperties;
import com.marketplace.laptop.entity.Laptop;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * THE discount decision. Pure function of (laptop, requested, rounds, repeat).
 * Same conversation shape -> same approved number, every time.
 *
 * The LLM frames and negotiates. It never picks the figure, and it cannot
 * reach a number this class did not return -- /orders re-derives the price
 * from the persisted offer rather than trusting anything the agent says.
 */
@Component
public class DiscountApprovalPolicy {

    private final MarketplaceProperties props;

    public DiscountApprovalPolicy(MarketplaceProperties props) {
        this.props = props;
    }

    public record Decision(
            BigDecimal approvedPct,
            BigDecimal requestedPct,
            BigDecimal formulaCapPct,
            BigDecimal merchantCapPct,
            int negotiationRoundsCounted,
            boolean cappedByMerchant,
            boolean cappedByFormula,
            boolean repeatBuyerPenaltyApplied,
            String reason
    ) {}

    public Decision decide(Laptop laptop, BigDecimal requestedPct, int negotiationRounds, boolean repeatRedeemer) {
        var cfg = props.getDiscount();

        BigDecimal requested = requestedPct == null ? BigDecimal.ZERO : requestedPct.max(BigDecimal.ZERO);
        int rounds = Math.max(0, negotiationRounds);
        int countedRounds = Math.min(rounds, cfg.getMaxRoundsCounted());

        BigDecimal formulaCap;
        if (repeatRedeemer) {
            // Already took a discount on this exact laptop recently: do not let
            // the ladder reset to baseline on a fresh conversation.
            formulaCap = cfg.getRepeatBuyerCapPct();
        } else {
            formulaCap = cfg.getBasePct()
                    .add(cfg.getPerRoundBonusPct().multiply(BigDecimal.valueOf(countedRounds)));
        }

        BigDecimal merchantCap = laptop.getMaxDiscountPct() == null
                ? BigDecimal.ZERO : laptop.getMaxDiscountPct();

        BigDecimal approved = requested.min(merchantCap).min(formulaCap)
                .setScale(2, RoundingMode.HALF_UP);

        boolean byMerchant = merchantCap.compareTo(requested) < 0 && merchantCap.compareTo(formulaCap) <= 0;
        boolean byFormula = formulaCap.compareTo(requested) < 0 && formulaCap.compareTo(merchantCap) < 0;

        String reason;
        if (repeatRedeemer) {
            reason = "REPEAT_REDEMPTION_CAP";
        } else if (byMerchant) {
            reason = "MERCHANT_CEILING";
        } else if (byFormula) {
            reason = "NEGOTIATION_STAGE_CAP";
        } else {
            reason = "REQUEST_GRANTED_IN_FULL";
        }

        return new Decision(approved, requested, formulaCap, merchantCap, countedRounds,
                byMerchant, byFormula, repeatRedeemer, reason);
    }
}
