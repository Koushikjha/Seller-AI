package com.marketplace.smartphone.repository;

import com.marketplace.smartphone.entity.Smartphone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SmartphoneRepository extends JpaRepository<Smartphone, UUID>,
        JpaSpecificationExecutor<Smartphone> {
}
