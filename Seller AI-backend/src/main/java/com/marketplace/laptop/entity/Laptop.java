package com.marketplace.laptop.entity;

import com.marketplace.catalog.entity.Cpu;
import com.marketplace.catalog.entity.Gpu;
import com.marketplace.catalog.entity.SubBrand;
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

@Entity
@Table(name = "laptop")
@Getter
@Setter
public class Laptop {

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

    /** null = integrated graphics only. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "gpu_id")
    private Gpu gpu;

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

    @Column(name = "ram_type", length = 10)
    private String ramType;

    @Column(name = "storage_gb", nullable = false)
    private int storageGb;

    @Column(name = "storage_type", length = 10)
    private String storageType;

    @Column(name = "display_inches", precision = 3, scale = 1)
    private BigDecimal displayInches;

    @Column(name = "display_type", length = 20)
    private String displayType;

    @Column(name = "refresh_rate_hz")
    private Integer refreshRateHz;

    @Column(name = "touchscreen", nullable = false)
    private boolean touchscreen;

    @Column(name = "weight_kg", precision = 3, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "battery_hours")
    private Integer batteryHours;

    @Column(length = 30)
    private String os;

    @Column(name = "release_year")
    private Integer releaseYear;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_specs")
    private Map<String, Object> extraSpecs = new LinkedHashMap<>();

    /** Image URLs or paths, in display order. Empty is normal. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "images")
    private List<String> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isInStock() {
        return stockQty > 0;
    }
}