package com.marketplace.catalog.repository;

import com.marketplace.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    Optional<Brand> findByNameIgnoreCase(String name);
}
