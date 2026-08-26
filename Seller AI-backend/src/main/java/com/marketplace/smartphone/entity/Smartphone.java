package com.marketplace.smartphone.entity;

import com.marketplace.catalog.entity.Cpu;
import com.marketplace.catalog.entity.SubBrand;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Second device type. Deliberately thin: it exists to prove that adding a
 * category means a new package implementing CatalogProvider, and nothing in
 * catalog/, laptop/ or the agent layer changing.
 */
@Entity
@Table(name = "smartphone")
@Getter
@Setter
public class Smartphone {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sub_brand_id", nullable = false)
    private SubBrand subBrand;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cpu_id", nullable = false)
    private Cpu cpu;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "max_discount_pct", nullable = false, precision = 4, scale = 2)
    private BigDecimal maxDiscountPct = BigDecimal.ZERO;

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(name = "ram_gb", nullable = false)
    private int ramGb;

    @Column(name = "storage_gb", nullable = false)
    private int storageGb;

    @Column(name = "display_inches", precision = 3, scale = 1)
    private BigDecimal displayInches;

    @Column(name = "refresh_rate_hz")
    private Integer refreshRateHz;

    @Column(name = "battery_mah")
    private Integer batteryMah;

    @Column(name = "main_camera_mp")
    private Integer mainCameraMp;

    @Column(length = 30)
    private String os;

    @Column(name = "release_year")
    private Integer releaseYear;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_specs")
    private Map<String, Object> extraSpecs = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public boolean isInStock() { return stockQty > 0; }
}
