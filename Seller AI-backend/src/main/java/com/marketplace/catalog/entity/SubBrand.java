package com.marketplace.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "sub_brand")
@Getter
@Setter
public class SubBrand {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 30)
    private String segment;

    @Column(name = "price_tier", length = 20)
    private String priceTier;

    @Column(name = "build_quality_tier", length = 60)
    private String buildQualityTier;

    @Column(name = "target_persona", length = 60)
    private String targetPersona;
}
