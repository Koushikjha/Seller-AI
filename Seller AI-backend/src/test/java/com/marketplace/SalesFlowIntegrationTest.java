package com.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Walks the close: verify identity -> negotiate -> order -> pay, plus the three
 * ways a close is supposed to fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode getData(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    private JsonNode postData(String url, String body, int expectedStatus) throws Exception {
        String res = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res);
    }

    private String verify(String key) throws Exception {
        postData("/identity/verify", "{\"identityKey\":\"" + key + "\"}", 200);
        return key;
    }

    private JsonNode negotiableLaptop() throws Exception {
        for (JsonNode l : getData("/laptops/search?limit=25")) {
            if (l.get("maxDiscountPct").asDouble() > 0 && l.get("stockQty").asInt() > 0) {
                return l;
            }
        }
        throw new IllegalStateException("seed data has no negotiable in-stock laptop");
    }

    @Test
    @DisplayName("full close: verify -> discount -> order -> payment link, stock drops by one")
    void happyPath() throws Exception {
        String identity = verify("happy@example.test");
        JsonNode laptop = negotiableLaptop();
        String laptopId = laptop.get("id").asText();
        int stockBefore = laptop.get("stockQty").asInt();

        JsonNode offer = postData("/discounts/request", """
                {"laptopId":"%s","identityKey":"%s","requestedPct":10,"negotiationRounds":2}
                """.formatted(laptopId, identity), 200).get("data");

        assertThat(offer.get("approvedPct").asDouble()).isGreaterThan(0);
        assertThat(offer.get("approvedPct").asDouble()).isLessThanOrEqualTo(10.0);
        assertThat(offer.get("priceAfterDiscount").asDouble())
                .isLessThan(offer.get("listPrice").asDouble());

        JsonNode order = postData("/orders", """
                {"laptopId":"%s","identityKey":"%s","discountOfferId":"%s"}
                """.formatted(laptopId, identity, offer.get("offerId").asText()), 201).get("data");

        assertThat(order.get("status").asText()).isEqualTo("CREATED");
        assertThat(order.get("finalPrice").asDouble())
                .isEqualTo(offer.get("priceAfterDiscount").asDouble());

        JsonNode paid = postData("/orders/" + order.get("orderId").asText() + "/payment-link", "", 200).get("data");
        assertThat(paid.get("paymentLink").asText()).isNotBlank();

        assertThat(getData("/laptops/" + laptopId).get("stockQty").asInt()).isEqualTo(stockBefore - 1);
    }

    @Test
    @DisplayName("an unverified identity cannot get a discount")
    void discountRequiresVerifiedIdentity() throws Exception {
        String laptopId = negotiableLaptop().get("id").asText();
        JsonNode res = postData("/discounts/request", """
                {"laptopId":"%s","identityKey":"ghost@example.test","requestedPct":5}
                """.formatted(laptopId), 409);
        assertThat(res.get("error").get("code").asText()).isEqualTo("IDENTITY_NOT_VERIFIED");
    }

    @Test
    @DisplayName("an offer cannot be redeemed twice")
    void offerIsSingleUse() throws Exception {
        String identity = verify("doubledip@example.test");
        String laptopId = negotiableLaptop().get("id").asText();

        JsonNode offer = postData("/discounts/request", """
                {"laptopId":"%s","identityKey":"%s","requestedPct":5,"negotiationRounds":1}
                """.formatted(laptopId, identity), 200).get("data");
        String offerId = offer.get("offerId").asText();

        postData("/orders", """
                {"laptopId":"%s","identityKey":"%s","discountOfferId":"%s"}
                """.formatted(laptopId, identity, offerId), 201);

        JsonNode second = postData("/orders", """
                {"laptopId":"%s","identityKey":"%s","discountOfferId":"%s"}
                """.formatted(laptopId, identity, offerId), 409);
        assertThat(second.get("error").get("code").asText()).isEqualTo("OFFER_ALREADY_REDEEMED");
    }

    @Test
    @DisplayName("an offer issued to one identity cannot be used by another")
    void offerIsBoundToIdentity() throws Exception {
        String owner = verify("owner@example.test");
        String thief = verify("thief@example.test");
        String laptopId = negotiableLaptop().get("id").asText();

        JsonNode offer = postData("/discounts/request", """
                {"laptopId":"%s","identityKey":"%s","requestedPct":5,"negotiationRounds":1}
                """.formatted(laptopId, owner), 200).get("data");

        JsonNode res = postData("/orders", """
                {"laptopId":"%s","identityKey":"%s","discountOfferId":"%s"}
                """.formatted(laptopId, thief, offer.get("offerId").asText()), 409);
        assertThat(res.get("error").get("code").asText()).isEqualTo("OFFER_IDENTITY_MISMATCH");
    }

    @Test
    @DisplayName("closing on an out-of-stock model fails cleanly instead of silently succeeding")
    void outOfStockCloseFails() throws Exception {
        String identity = verify("unlucky@example.test");
        JsonNode outOfStock = null;
        for (JsonNode l : getData("/laptops")) {
            if (l.get("stockQty").asInt() == 0) {
                outOfStock = l;
                break;
            }
        }
        assertThat(outOfStock).as("seed data includes an out-of-stock model").isNotNull();

        JsonNode res = postData("/orders", """
                {"laptopId":"%s","identityKey":"%s"}
                """.formatted(outOfStock.get("id").asText(), identity), 409);
        assertThat(res.get("error").get("code").asText()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    @DisplayName("a non-negotiable laptop approves a zero discount rather than refusing to answer")
    void nonNegotiableLaptopApprovesZero() throws Exception {
        String identity = verify("firmprice@example.test");
        JsonNode fixed = null;
        for (JsonNode l : getData("/laptops/search?limit=25")) {
            if (l.get("maxDiscountPct").asDouble() == 0.0) {
                fixed = l;
                break;
            }
        }
        assertThat(fixed).as("seed data includes a non-negotiable model").isNotNull();

        JsonNode offer = postData("/discounts/request", """
                {"laptopId":"%s","identityKey":"%s","requestedPct":10,"negotiationRounds":3}
                """.formatted(fixed.get("id").asText(), identity), 200).get("data");

        assertThat(offer.get("approvedPct").asDouble()).isEqualTo(0.0);
        assertThat(offer.get("reason").asText()).isEqualTo("MERCHANT_CEILING");
    }

    @Test
    @DisplayName("a failed payment releases the held unit")
    void failedPaymentRestoresStock() throws Exception {
        String identity = verify("failedpay@example.test");
        JsonNode laptop = negotiableLaptop();
        String laptopId = laptop.get("id").asText();
        int stockBefore = laptop.get("stockQty").asInt();

        JsonNode order = postData("/orders", """
                {"laptopId":"%s","identityKey":"%s"}
                """.formatted(laptopId, identity), 201).get("data");

        assertThat(getData("/laptops/" + laptopId).get("stockQty").asInt()).isEqualTo(stockBefore - 1);

        postData("/orders/" + order.get("orderId").asText() + "/settle?paid=false", "", 200);

        assertThat(getData("/laptops/" + laptopId).get("stockQty").asInt()).isEqualTo(stockBefore);
    }
}
