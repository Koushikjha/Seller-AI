package com.marketplace.agent.state;

import com.marketplace.identity.entity.VerifiedIdentity;
import com.marketplace.laptop.entity.DiscountOffer;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.entity.MarketplaceOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structured sales state, held outside the LLM.
 *
 * The model receives a rendered summary of this each turn and can influence
 * it only through tool calls. It cannot write to it directly, which is why
 * a conversation survives a restart, a model swap, or a context truncation.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
public class Conversation {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "identity_key")
    private VerifiedIdentity identity;

    @Column(name = "device_type", nullable = false, length = 20)
    private String deviceType = "LAPTOP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesStage stage = SalesStage.DISCOVERY;

    @Column(name = "technical_level", length = 20)
    private String technicalLevel;

    @Column(name = "budget_max", precision = 10, scale = 2)
    private BigDecimal budgetMax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requirements")
    private Map<String, Object> requirements = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_ids")
    private List<String> candidateIds = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "objections")
    private List<String> objections = new ArrayList<>();

    /**
     * Incremented by the orchestrator whenever a discount is requested.
     * The LLM's own count is discarded — see SalesAgentService. This is the
     * difference between a negotiation ladder and a number the model can
     * talk its way up.
     */
    @Column(name = "negotiation_rounds", nullable = false)
    private int negotiationRounds;

    @Column(name = "questions_asked", nullable = false)
    private int questionsAsked;

    @Column(name = "tool_calls_total", nullable = false)
    private int toolCallsTotal;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "selected_laptop_id")
    private Laptop selectedLaptop;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "discount_offer_id")
    private DiscountOffer discountOffer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private MarketplaceOrder order;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public String identityKey() {
        return identity == null ? null : identity.getIdentityKey();
    }
}
