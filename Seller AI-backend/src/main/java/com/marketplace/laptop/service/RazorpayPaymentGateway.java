package com.marketplace.laptop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketplace.common.BusinessRuleException;
import com.marketplace.config.RazorpayProperties;
import com.marketplace.laptop.entity.MarketplaceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Razorpay, via the Payment Links API.
 *
 * Payment Links rather than Orders + Checkout.js on purpose. The existing
 * {@link PaymentGateway} seam is already {@code order -> (providerRef, url)},
 * which is precisely the shape of a payment link, so nothing in OrderService,
 * the agent or the discount layer changes. It also needs no frontend SDK and no
 * public tunnel to demo: the customer opens a URL.
 *
 * Deliberately hand-rolled over RestClient instead of the Razorpay SDK. Two
 * endpoints and HTTP Basic auth do not justify a dependency, and being able to
 * read exactly what goes on the wire has already been worth more than
 * convenience on this project.
 *
 * Amounts go to Razorpay in paise as an integer. Rupees are re-derived from the
 * persisted order here and nowhere else — the agent never sees this number and
 * cannot influence it.
 */
public class RazorpayPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);

    private final RazorpayProperties cfg;
    private final RestClient http;

    public RazorpayPaymentGateway(RazorpayProperties cfg) {
        this.cfg = cfg;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(cfg.getTimeoutSeconds()));
        this.http = RestClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", basicAuth())
                .build();
    }

    @Override
    public String name() {
        return "razorpay" + (cfg.testMode() ? ":test" : ":live");
    }

    @Override
    public PaymentLink createPaymentLink(MarketplaceOrder order) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", paise(order.getFinalPrice()));
        body.put("currency", "INR");
        body.put("accept_partial", false);
        body.put("description", order.getLaptop().getModelName());
        body.put("expire_by", Instant.now().plusSeconds(60L * cfg.getExpiryMinutes()).getEpochSecond());
        body.put("reference_id", order.getId().toString());
        body.put("callback_url", cfg.getCallbackUrl());
        body.put("callback_method", "get");
        body.put("customer", customerOf(order.getIdentityKey()));
        // The customer already agreed to this on the shop floor. Razorpay sending
        // its own SMS and email on top is noise, and on test keys it is noise to
        // a real inbox.
        body.put("notify", Map.of("sms", false, "email", false));
        body.put("reminder_enable", false);
        // notes come back on the webhook payload, which is how a webhook that
        // arrives with no reference_id still finds its order.
        body.put("notes", Map.of("orderId", order.getId().toString(),
                                 "laptopId", order.getLaptop().getId().toString()));

        JsonNode res = post("/payment_links", body);
        String id = res.path("id").asText(null);
        String url = res.path("short_url").asText(null);
        if (id == null || url == null) {
            throw new BusinessRuleException("PAYMENT_LINK_FAILED",
                    "Razorpay accepted the request but returned no payment link", null);
        }
        log.info("Razorpay payment link {} created for order {} ({} paise)",
                id, order.getId(), body.get("amount"));
        return new PaymentLink(id, url);
    }

    /**
     * Ask Razorpay what actually happened, rather than waiting to be told.
     *
     * The webhook is the correct mechanism and it is implemented, but it needs a
     * public URL. On a laptop behind NAT — which is where this gets demonstrated —
     * polling this is the difference between a working demo and a tunnel that
     * chose today to fail.
     *
     * @return true when the link is fully paid, false while it is not
     */
    public boolean isPaid(String paymentLinkId) {
        JsonNode res = get("/payment_links/" + paymentLinkId);
        String status = res.path("status").asText("");
        log.debug("Razorpay payment link {} status={}", paymentLinkId, status);
        return "paid".equals(status);
    }

    // ------------------------------------------------------------------

    /**
     * Rupees to paise, exactly. A double here would eventually round a customer's
     * total by a paisa, and the one thing a payment amount may never be is
     * approximately right.
     */
    public static long paise(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.HALF_UP)
                     .movePointRight(2)
                     .longValueExact();
    }

    /**
     * The identity we verified is either an email or a phone — the same key the
     * discount offer was held against. Razorpay wants them in different fields.
     */
    private Map<String, Object> customerOf(String identityKey) {
        Map<String, Object> customer = new LinkedHashMap<>();
        if (identityKey != null && identityKey.contains("@")) {
            customer.put("email", identityKey);
        } else if (identityKey != null && !identityKey.isBlank()) {
            customer.put("contact", identityKey);
        }
        return customer;
    }

    private String basicAuth() {
        String raw = cfg.getKeyId() + ":" + cfg.getKeySecret();
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            return http.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw razorpayError(e);
        }
    }

    private JsonNode get(String path) {
        try {
            return http.get().uri(path).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw razorpayError(e);
        }
    }

    /**
     * Razorpay's own error description, surfaced rather than swallowed. "Payment
     * failed" tells you nothing at 2am; "amount must be at least 100" tells you
     * the seed price is in rupees and you sent rupees.
     */
    private BusinessRuleException razorpayError(RestClientResponseException e) {
        String detail = e.getResponseBodyAsString();
        log.error("Razorpay {} — {}", e.getStatusCode(), detail);
        return new BusinessRuleException("PAYMENT_GATEWAY_ERROR",
                "Razorpay rejected the request (" + e.getStatusCode() + ")",
                Map.of("razorpay", detail));
    }
}
