package com.marketplace.webinfo.entity;

import com.marketplace.catalog.entity.SubBrand;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached web summary, keyed by sub-brand rather than by individual laptop:
 * companion software and driver cadence are properties of a product line, so
 * one cache row serves every SKU in it and API calls drop sharply.
 */
@Entity
@Table(name = "sub_brand_web_cache")
@Getter
@Setter
public class SubBrandWebCache {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "sub_brand_id", nullable = false)
    private SubBrand subBrand;

    @Column(name = "query_hash", nullable = false, length = 64)
    private String queryHash;

    @Column(name = "query_text", length = 300)
    private String queryText;

    @Column(length = 8000)
    private String summary;

    @Column(name = "source_count", nullable = false)
    private int sourceCount;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt = Instant.now();
}
