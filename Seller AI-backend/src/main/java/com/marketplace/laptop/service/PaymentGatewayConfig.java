package com.marketplace.laptop.service;

import com.marketplace.laptop.entity.MarketplaceOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Default payment wiring. Define your own {@link PaymentGateway} bean (Razorpay,
 * Stripe, whatever) and it wins automatically -- nothing else changes.
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(PaymentGateway.class)
    PaymentGateway stubPaymentGateway() {
        return new PaymentGateway() {
            @Override public String name() { return "stub"; }

            @Override
            public PaymentLink createPaymentLink(MarketplaceOrder order) {
                String ref = "stub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
                return new PaymentLink(ref, "https://payments.example.test/pay/" + ref);
            }
        };
    }
}
