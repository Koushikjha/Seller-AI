package com.marketplace.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "gpu")
@Getter
@Setter
public class Gpu {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @Column(length = 30)
    private String manufacturer;

    @Column(name = "vram_gb")
    private Integer vramGb;

    @Column(name = "benchmark_tier", length = 20)
    private String benchmarkTier;

    @Column(name = "is_integrated", nullable = false)
    private boolean integrated = false;
}
