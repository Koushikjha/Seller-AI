package com.marketplace.catalog.repository;

import com.marketplace.catalog.entity.Cpu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CpuRepository extends JpaRepository<Cpu, UUID> {
    Optional<Cpu> findByNameIgnoreCase(String name);
}
