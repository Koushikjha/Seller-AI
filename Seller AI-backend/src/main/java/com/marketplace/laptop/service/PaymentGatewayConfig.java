package com.marketplace.laptop.service;

import com.marketplace.config.RazorpayProperties;
import com.marketplace.laptop.entity.MarketplaceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Payment wiring: Razorpay when it is configured, the stub otherwise.
 *
 * One method with an if, rather than two beans racing on
 * {@code @ConditionalOnMissingBean}. Ordering between two user configuration
 * classes is not guaranteed, and "which gateway did I actually get" is not a
 * question anyone should have to answer by reading Spring's bean graph — least
 * of all five minutes before a demo. Same reasoning as LlmClientConfig, and the
 * startup line tells you the answer without asking.
 */
@Configuration
public class PaymentGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfig.class);

    @Bean
    PaymentGateway paymentGateway(RazorpayProperties razorpay) {
        if (razorpay.configured()) {
            log.info("Payments via Razorpay in {} MODE (key {})",
                    razorpay.testMode() ? "TEST" : "LIVE", razorpay.getKeyId());
            if (!razorpay.testMode()) {
                log.warn("This is a LIVE Razorpay key. Orders placed here take real money.");
            }
            if (razorpay.getWebhookSecret() == null || razorpay.getWebhookSecret().isBlank()) {
                log.warn("No marketplace.razorpay.webhook-secret set — webhook calls will be "
                        + "rejected. Settle by polling /orders/{id}/refresh-payment instead.");
            }
            return new RazorpayPaymentGateway(razorpay);
        }
        log.info("Payments using the offline stub gateway (no RAZORPAY_KEY_ID set). "
                + "Orders and links are real rows; no money moves.");
        return stub();
    }

    /**
     * Keeps the entire order flow runnable — and the test suite green — with no
     * Razorpay account at all. Every scenario except "did the money arrive" is
     * exercised by this.
     */
    private PaymentGateway stub() {
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