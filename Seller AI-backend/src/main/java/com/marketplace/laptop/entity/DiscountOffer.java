package com.marketplace.laptop.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A concrete, expiring discount the backend has approved. Every negotiation
 * turn that produces a number is persisted -- both so the order endpoint can
 * verify what was actually promised, and so abuse patterns are analysable.
 */
@Entity
@Table(name = "discount_offer")
@Getter
@Setter
public class DiscountOffer {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "laptop_id", nullable = false)
    private Laptop laptop;

    @Column(name = "identity_key", nullable = false, length = 120)
    private String identityKey;

    @Column(name = "requested_pct", precision = 4, scale = 2)
    private BigDecimal requestedPct;

    @Column(name = "approved_pct", precision = 4, scale = 2)
    private BigDecimal approvedPct;

    @Column(name = "negotiation_rounds", nullable = false)
    private int negotiationRounds;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean redeemed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return !redeemed && !isExpired();
    }
}
