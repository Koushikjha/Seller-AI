package com.marketplace.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "brand")
@Getter
@Setter
public class Brand {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 60, unique = true)
    private String name;

    @Column(name = "country_of_origin", length = 60)
    private String countryOfOrigin;

    @Column(name = "support_tier", length = 20)
    private String supportTier;

    @Column(name = "default_warranty_months")
    private Integer defaultWarrantyMonths;

    @Column(name = "brand_positioning", length = 120)
    private String brandPositioning;
}
