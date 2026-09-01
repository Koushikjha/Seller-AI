package com.marketplace.laptop.repository;

import java.util.Optional;
import com.marketplace.laptop.entity.MarketplaceOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<MarketplaceOrder, UUID> {
    List<MarketplaceOrder> findByIdentityKeyOrderByCreatedAtDesc(String identityKey);

    /** Razorpay's payment-link id, stored when the link was created. */
    Optional<MarketplaceOrder> findByPaymentRef(String paymentRef);
}
