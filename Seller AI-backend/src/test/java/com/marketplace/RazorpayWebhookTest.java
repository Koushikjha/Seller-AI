package com.marketplace;

import com.marketplace.laptop.service.RazorpayPaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook marks orders paid, so an unauthenticated caller reaching it is the
 * difference between a shop and a giveaway. These tests exist because that is
 * exactly the kind of control everybody believes they have and nobody checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RazorpayWebhookTest {


    private static final String SECRET = "test_webhook_secret";

    @Autowired MockMvc mvc;

    private static final String BODY = """
            {"event":"payment_link.paid","payload":{"payment_link":{"entity":{
              "id":"plink_TEST","reference_id":"00000000-0000-0000-0000-000000000001"}}}}""";

    @Test
    @DisplayName("a webhook with no signature header is rejected")
    void unsignedIsRejected() throws Exception {
        mvc.perform(post("/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a webhook signed with the wrong secret is rejected")
    void wrongSignatureIsRejected() throws Exception {
        mvc.perform(post("/webhooks/razorpay")
                        .header("X-Razorpay-Signature", sign(BODY, "not_the_secret"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a correctly signed webhook is accepted, and an unknown order is not an error")
    void signedIsAcceptedAndUnknownOrderIsIgnored() throws Exception {
        // 200 with handled=false: Razorpay retries anything non-2xx, and retrying
        // an event we can never place is noise rather than resilience.
        String res = mvc.perform(post("/webhooks/razorpay")
                        .header("X-Razorpay-Signature", sign(BODY, SECRET))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(res).contains("\"ok\":true").contains("\"handled\":false");
    }

    @Test
    @DisplayName("rupees convert to paise exactly, with no floating point anywhere")
    void paiseConversionIsExact() {
        assertThat(RazorpayPaymentGateway.paise(new BigDecimal("89990"))).isEqualTo(8_999_000L);
        assertThat(RazorpayPaymentGateway.paise(new BigDecimal("86390.40"))).isEqualTo(8_639_040L);
        assertThat(RazorpayPaymentGateway.paise(new BigDecimal("0.01"))).isEqualTo(1L);
        // 0.1 + 0.2 territory: the one number in the system that may never be
        // approximately right.
        assertThat(RazorpayPaymentGateway.paise(new BigDecimal("124990.005"))).isEqualTo(12_499_001L);
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
