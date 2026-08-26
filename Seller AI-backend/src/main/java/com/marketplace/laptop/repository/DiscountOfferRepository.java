package com.marketplace.laptop.repository;

import com.marketplace.laptop.entity.DiscountOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DiscountOfferRepository extends JpaRepository<DiscountOffer, UUID> {

    List<DiscountOffer> findByIdentityKeyAndLaptopIdOrderByCreatedAtDesc(String identityKey, UUID laptopId);

    long countByIdentityKeyAndLaptopIdAndRedeemedTrueAndCreatedAtAfter(
            String identityKey, UUID laptopId, Instant after);

    List<DiscountOffer> findByIdentityKeyOrderByCreatedAtDesc(String identityKey);
}
