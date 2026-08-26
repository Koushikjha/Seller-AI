package com.marketplace.catalog.repository;

import com.marketplace.catalog.entity.SubBrand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubBrandRepository extends JpaRepository<SubBrand, UUID> {
    List<SubBrand> findByBrandId(UUID brandId);
    Optional<SubBrand> findByNameIgnoreCase(String name);
}
