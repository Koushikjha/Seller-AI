package com.marketplace.laptop.service;

import com.marketplace.laptop.entity.MarketplaceOrder;

/**
 * Payment provider seam. The stub below keeps the whole order flow runnable
 * with no external account; dropping in Razorpay later means one more
 * implementation of this interface and a config switch -- no change anywhere
 * in the order, discount or agent layers.
 */
public interface PaymentGateway {

    record PaymentLink(String providerRef, String url) {}

    String name();

    PaymentLink createPaymentLink(MarketplaceOrder order);
}
