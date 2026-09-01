package com.marketplace.laptop.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.config.RazorpayProperties;
import com.marketplace.laptop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay webhook receiver.
 *
 * This endpoint marks orders paid, so it is the one place in the application
 * where an unauthenticated stranger could hand himself a laptop. Three rules
 * follow from that and none of them are negotiable:
 *
 *   1. The raw request body is verified against an HMAC-SHA256 signature before
 *      it is parsed. Not after — a parsed body is a body you have already
 *      trusted enough to run a JSON parser over.
 *   2. No webhook secret configured means every call is rejected. An endpoint
 *      that fails open is worse than one that does not exist, because you
 *      believe you have a control.
 *   3. The comparison is constant-time. Timing a byte-at-a-time comparison to
 *      recover a signature is old, well-documented, and entirely avoidable.
 *
 * The amount is never read from the payload. Razorpay is asked whether the link
 * was paid; what it was paid for comes from our own row. A webhook that says
 * "paid ₹1" cannot buy a ₹90,000 laptop here, because that number is not an
 * input to anything.
 */
@RestController
@RequestMapping("/webhooks/razorpay")
@Tag(name = "payments", description = "Razorpay webhook receiver (signature-verified)")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final RazorpayProperties cfg;
    private final OrderService orders;
    private final ObjectMapper mapper;

    public RazorpayWebhookController(RazorpayProperties cfg, OrderService orders, ObjectMapper mapper) {
        this.cfg = cfg;
        this.orders = orders;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Razorpay payment_link.paid / payment.failed events")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (cfg.getWebhookSecret() == null || cfg.getWebhookSecret().isBlank()) {
            log.warn("Razorpay webhook received but no webhook secret is configured — rejecting.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "reason", "webhook secret not configured"));
        }
        if (signature == null || !verify(rawBody, signature)) {
            log.warn("Razorpay webhook with a bad or missing signature — rejecting.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "reason", "signature mismatch"));
        }

        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "reason", "unparseable body"));
        }

        String event = root.path("event").asText("");
        JsonNode link = root.path("payload").path("payment_link").path("entity");
        UUID orderId = resolveOrderId(link);

        if (orderId == null) {
            // 200 on purpose. Razorpay retries non-2xx, and retrying an event we
            // will never be able to place is noise, not resilience.
            log.info("Razorpay event {} did not resolve to a known order — ignoring.", event);
            return ResponseEntity.ok(Map.of("ok", true, "handled", false, "event", event));
        }

        boolean changed = switch (event) {
            case "payment_link.paid" -> orders.settleIfPending(orderId, true);
            case "payment_link.expired", "payment_link.cancelled" ->
                    orders.settleIfPending(orderId, false);
            default -> false;
        };

        log.info("Razorpay event {} for order {} — {}", event, orderId,
                changed ? "settled" : "no change (already settled or not applicable)");
        return ResponseEntity.ok(Map.of("ok", true, "handled", changed, "event", event));
    }

    /**
     * reference_id is our own order id and is set when the link is created.
     * notes.orderId is the same value by another route, kept because Razorpay's
     * payload shape has moved before and one of the two has always been present.
     * The payment-link id is the last resort, looked up against our row.
     */
    private UUID resolveOrderId(JsonNode link) {
        UUID byReference = asUuid(link.path("reference_id").asText(null));
        if (byReference != null) return byReference;

        UUID byNote = asUuid(link.path("notes").path("orderId").asText(null));
        if (byNote != null) return byNote;

        String linkId = link.path("id").asText(null);
        if (linkId == null) return null;
        return orders.byPaymentRef(linkId).map(o -> o.getId()).orElse(null);
    }

    private UUID asUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean verify(String rawBody, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    cfg.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(
                    mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return constantTimeEquals(expected, signature);
        } catch (Exception e) {
            log.error("Could not compute the webhook signature", e);
            return false;
        }
    }

    /** Length-independent, difference-accumulating compare. No early return. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int diff = x.length ^ y.length;
        for (int i = 0; i < x.length && i < y.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }
}
