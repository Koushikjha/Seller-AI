package com.marketplace.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cpu")
@Getter
@Setter
public class Cpu {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @Column(length = 30)
    private String manufacturer;

    private Integer cores;
    private Integer threads;

    @Column(name = "base_clock_ghz", precision = 3, scale = 1)
    private BigDecimal baseClockGhz;

    @Column(name = "boost_clock_ghz", precision = 3, scale = 1)
    private BigDecimal boostClockGhz;

    @Column(name = "tdp_watts")
    private Integer tdpWatts;

    @Column(name = "benchmark_tier", length = 20)
    private String benchmarkTier;
}
