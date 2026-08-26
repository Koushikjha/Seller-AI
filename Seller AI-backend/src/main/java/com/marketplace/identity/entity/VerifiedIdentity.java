package com.marketplace.identity.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A verified phone/email. Discounts and orders are keyed to this, never to a
 * chat session id -- otherwise a customer could reopen the chat and farm a
 * fresh negotiation ladder every time.
 */
@Entity
@Table(name = "verified_identity")
@Getter
@Setter
public class VerifiedIdentity {

    @Id
    @Column(name = "identity_key", length = 120, nullable = false, updatable = false)
    private String identityKey;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt = Instant.now();
}
