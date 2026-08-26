package com.marketplace.webinfo.repository;

import com.marketplace.webinfo.entity.SubBrandWebCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubBrandWebCacheRepository extends JpaRepository<SubBrandWebCache, UUID> {
    Optional<SubBrandWebCache> findBySubBrandIdAndQueryHash(UUID subBrandId, String queryHash);
}
