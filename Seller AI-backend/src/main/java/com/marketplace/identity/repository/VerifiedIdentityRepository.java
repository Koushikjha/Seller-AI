package com.marketplace.identity.repository;

import com.marketplace.identity.entity.VerifiedIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface VerifiedIdentityRepository extends JpaRepository<VerifiedIdentity, String> {
    long countByIpAddressAndVerifiedAtAfter(String ipAddress, Instant after);
}
