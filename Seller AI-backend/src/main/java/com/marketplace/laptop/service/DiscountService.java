package com.marketplace.laptop.service;

import com.marketplace.common.BusinessRuleException;
import com.marketplace.common.NotFoundException;
import com.marketplace.config.MarketplaceProperties;
import com.marketplace.identity.service.IdentityService;
import com.marketplace.laptop.dto.DiscountOfferDto;
import com.marketplace.laptop.dto.DiscountRequest;
import com.marketplace.laptop.entity.DiscountOffer;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.DiscountOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DiscountService {

    private final DiscountOfferRepository offers;
    private final LaptopService laptops;
    private final IdentityService identities;
    private final DiscountApprovalPolicy policy;
    private final MarketplaceProperties props;

    public DiscountService(DiscountOfferRepository offers, LaptopService laptops,
                           IdentityService identities, DiscountApprovalPolicy policy,
                           MarketplaceProperties props) {
        this.offers = offers;
        this.laptops = laptops;
        this.identities = identities;
        this.policy = policy;
        this.props = props;
    }

    /**
     * What the agent may know before negotiating.
     *
     * openingPct is what the formula grants at round 0. maxPossiblePct is the
     * merchant ceiling and is included for the merchant dashboard -- the agent
     * prompt must never quote it to a customer, because quoting the ceiling is
     * the same as giving it away. Only /discounts/request produces a number the
     * agent is allowed to say out loud.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> limit(UUID laptopId) {
        Laptop laptop = laptops.require(laptopId);
        var cfg = props.getDiscount();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("laptopId", laptopId);
        out.put("negotiable", laptop.getMaxDiscountPct().compareTo(BigDecimal.ZERO) > 0);
        out.put("openingPct", cfg.getBasePct().min(laptop.getMaxDiscountPct()));
        out.put("maxPossiblePct", laptop.getMaxDiscountPct());
        out.put("offerTtlMinutes", cfg.getOfferTtlMinutes());
        return out;
    }

    public DiscountOfferDto request(DiscountRequest req) {
        identities.require(req.identityKey());
        Laptop laptop = laptops.require(req.laptopId());

        if (laptop.getStockQty() <= 0) {
            throw new BusinessRuleException("OUT_OF_STOCK",
                    "This laptop is out of stock; no discount can be issued",
                    Map.of("laptopId", laptop.getId()));
        }

        String key = req.identityKey().trim().toLowerCase();
        Instant lookBack = Instant.now().minus(Duration.ofDays(props.getDiscount().getRepeatLookBackDays()));
        boolean repeat = offers.countByIdentityKeyAndLaptopIdAndRedeemedTrueAndCreatedAtAfter(
                key, laptop.getId(), lookBack) > 0;

        var decision = policy.decide(laptop, req.requestedPct(), req.rounds(), repeat);

        DiscountOffer offer = new DiscountOffer();
        offer.setLaptop(laptop);
        offer.setIdentityKey(key);
        offer.setRequestedPct(decision.requestedPct());
        offer.setApprovedPct(decision.approvedPct());
        offer.setNegotiationRounds(req.rounds());
        offer.setExpiresAt(Instant.now().plus(Duration.ofMinutes(props.getDiscount().getOfferTtlMinutes())));
        offer.setCreatedAt(Instant.now());

        return DiscountOfferDto.from(offers.save(offer), decision.reason());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validity(UUID offerId, String identityKey) {
        DiscountOffer offer = offers.findById(offerId)
                .orElseThrow(() -> new NotFoundException("DiscountOffer", offerId));

        boolean identityMatches = offer.getIdentityKey()
                .equalsIgnoreCase(identityKey == null ? "" : identityKey.trim());

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("offerId", offerId);
        out.put("valid", identityMatches && offer.isUsable());
        out.put("redeemed", offer.isRedeemed());
        out.put("expired", offer.isExpired());
        out.put("identityMatches", identityMatches);
        out.put("approvedPct", offer.getApprovedPct());
        out.put("expiresAt", offer.getExpiresAt());
        return out;
    }

    /** Called by OrderService inside the order transaction. */
    public DiscountOffer consume(UUID offerId, UUID laptopId, String identityKey) {
        DiscountOffer offer = offers.findById(offerId)
                .orElseThrow(() -> new NotFoundException("DiscountOffer", offerId));

        if (!offer.getLaptop().getId().equals(laptopId)) {
            throw new BusinessRuleException("OFFER_LAPTOP_MISMATCH",
                    "This discount offer was issued for a different laptop", null);
        }
        if (!offer.getIdentityKey().equalsIgnoreCase(identityKey.trim())) {
            throw new BusinessRuleException("OFFER_IDENTITY_MISMATCH",
                    "This discount offer belongs to a different identity", null);
        }
        if (offer.isRedeemed()) {
            throw new BusinessRuleException("OFFER_ALREADY_REDEEMED",
                    "This discount offer has already been used", null);
        }
        if (offer.isExpired()) {
            throw new BusinessRuleException("OFFER_EXPIRED",
                    "This discount offer has expired",
                    Map.of("expiredAt", offer.getExpiresAt()));
        }

        offer.setRedeemed(true);
        return offers.save(offer);
    }

    @Transactional(readOnly = true)
    public java.util.List<DiscountOfferDto> history(String identityKey) {
        return offers.findByIdentityKeyOrderByCreatedAtDesc(identityKey.trim().toLowerCase())
                .stream().map(o -> DiscountOfferDto.from(o, null)).toList();
    }
}
