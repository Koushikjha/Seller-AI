package com.marketplace.laptop.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marketplace_order")
@Getter
@Setter
public class MarketplaceOrder {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "laptop_id", nullable = false)
    private Laptop laptop;

    @Column(name = "identity_key", nullable = false, length = 120)
    private String identityKey;

    @Column(name = "list_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "discount_pct", nullable = false, precision = 4, scale = 2)
    private BigDecimal discountPct = BigDecimal.ZERO;

    @Column(name = "final_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "discount_offer_id")
    private DiscountOffer discountOffer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "payment_ref", length = 80)
    private String paymentRef;

    @Column(name = "payment_link", length = 500)
    private String paymentLink;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }
}
