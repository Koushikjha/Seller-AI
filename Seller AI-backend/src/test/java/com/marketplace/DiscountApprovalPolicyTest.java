package com.marketplace;

import com.marketplace.config.MarketplaceProperties;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.service.DiscountApprovalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The discount formula is the one place where a non-deterministic answer would
 * be a business bug rather than a UX wobble, so it gets a pure unit test.
 */
class DiscountApprovalPolicyTest {

    private DiscountApprovalPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DiscountApprovalPolicy(new MarketplaceProperties());
    }

    private Laptop laptopWithCeiling(String pct) {
        Laptop l = new Laptop();
        l.setMaxDiscountPct(new BigDecimal(pct));
        l.setBasePrice(new BigDecimal("80000"));
        return l;
    }

    @Test
    @DisplayName("round 0 grants only the base discount")
    void baseDiscountAtRoundZero() {
        var d = policy.decide(laptopWithCeiling("10.00"), new BigDecimal("10"), 0, false);
        assertThat(d.approvedPct()).isEqualByComparingTo("2.00");
        assertThat(d.reason()).isEqualTo("NEGOTIATION_STAGE_CAP");
    }

    @Test
    @DisplayName("each negotiation round adds the configured bonus")
    void roundsRaiseTheCap() {
        var l = laptopWithCeiling("20.00");
        assertThat(policy.decide(l, new BigDecimal("15"), 1, false).approvedPct()).isEqualByComparingTo("4.00");
        assertThat(policy.decide(l, new BigDecimal("15"), 2, false).approvedPct()).isEqualByComparingTo("6.00");
        assertThat(policy.decide(l, new BigDecimal("15"), 3, false).approvedPct()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("rounds beyond the counted maximum buy nothing — grinding does not pay")
    void roundsAreCapped() {
        var l = laptopWithCeiling("20.00");
        var atThree = policy.decide(l, new BigDecimal("15"), 3, false).approvedPct();
        var atTwenty = policy.decide(l, new BigDecimal("15"), 20, false).approvedPct();
        assertThat(atTwenty).isEqualByComparingTo(atThree);
    }

    @Test
    @DisplayName("merchant ceiling always wins over the formula")
    void merchantCeilingWins() {
        var d = policy.decide(laptopWithCeiling("3.00"), new BigDecimal("15"), 3, false);
        assertThat(d.approvedPct()).isEqualByComparingTo("3.00");
        assertThat(d.reason()).isEqualTo("MERCHANT_CEILING");
    }

    @Test
    @DisplayName("a non-negotiable laptop approves nothing at any round")
    void zeroCeilingApprovesNothing() {
        for (int rounds = 0; rounds <= 5; rounds++) {
            assertThat(policy.decide(laptopWithCeiling("0.00"), new BigDecimal("10"), rounds, false)
                    .approvedPct()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    @DisplayName("asking for less than the cap gets exactly what was asked for")
    void modestRequestGrantedInFull() {
        var d = policy.decide(laptopWithCeiling("10.00"), new BigDecimal("1.5"), 3, false);
        assertThat(d.approvedPct()).isEqualByComparingTo("1.50");
        assertThat(d.reason()).isEqualTo("REQUEST_GRANTED_IN_FULL");
    }

    @Test
    @DisplayName("a repeat redeemer cannot reset the ladder by starting a new conversation")
    void repeatRedeemerIsPenalised() {
        var d = policy.decide(laptopWithCeiling("10.00"), new BigDecimal("10"), 3, true);
        assertThat(d.approvedPct()).isEqualByComparingTo("1.00");
        assertThat(d.reason()).isEqualTo("REPEAT_REDEMPTION_CAP");
    }

    @Test
    @DisplayName("identical inputs always produce an identical number")
    void deterministic() {
        var l = laptopWithCeiling("10.00");
        var first = policy.decide(l, new BigDecimal("7"), 2, false).approvedPct();
        for (int i = 0; i < 50; i++) {
            assertThat(policy.decide(l, new BigDecimal("7"), 2, false).approvedPct())
                    .isEqualByComparingTo(first);
        }
    }

    @Test
    @DisplayName("a negative request is clamped rather than becoming a price increase")
    void negativeRequestClamped() {
        var d = policy.decide(laptopWithCeiling("10.00"), new BigDecimal("-5"), 2, false);
        assertThat(d.approvedPct()).isEqualByComparingTo("0.00");
    }
}
