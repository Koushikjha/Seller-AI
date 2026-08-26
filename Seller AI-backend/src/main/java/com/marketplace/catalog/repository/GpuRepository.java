package com.marketplace.catalog.repository;

import com.marketplace.catalog.entity.Gpu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GpuRepository extends JpaRepository<Gpu, UUID> {
    Optional<Gpu> findByNameIgnoreCase(String name);
}
