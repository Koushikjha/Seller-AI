package com.marketplace;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The per-IP verification limit, tested on its own.
 *
 * It runs in its own context with a deliberately tiny limit and supplies its
 * own client IP via X-Forwarded-For, so it neither depends on nor interferes
 * with the rest of the suite. This is the rule that stops one machine minting
 * a hundred identities to reset the negotiation ladder, so it deserves a real
 * test rather than being an incidental failure in unrelated ones.
 */
@SpringBootTest(properties = "marketplace.identity.max-verifications-per-ip-per-hour=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityRateLimitTest {

    private static final String IP = "203.0.113.7";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private int verify(String identityKey, String ip) throws Exception {
        return mvc.perform(post("/identity/verify")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityKey\":\"" + identityKey + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("new identities are allowed up to the limit, then refused")
    void limitIsEnforcedPerIp() throws Exception {
        assertThat(verify("rl-one@example.test", IP)).isEqualTo(200);
        assertThat(verify("rl-two@example.test", IP)).isEqualTo(200);
        assertThat(verify("rl-three@example.test", IP)).isEqualTo(200);

        assertThat(verify("rl-four@example.test", IP))
                .as("fourth new identity from the same IP is refused")
                .isEqualTo(409);
    }

    @Test
    @DisplayName("an already-verified identity does not consume the quota")
    void repeatVerificationIsFree() throws Exception {
        String ip = "203.0.113.9";
        assertThat(verify("rl-repeat@example.test", ip)).isEqualTo(200);

        for (int i = 0; i < 10; i++) {
            assertThat(verify("rl-repeat@example.test", ip))
                    .as("re-verifying a known identity stays allowed")
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the limit is per IP, not global")
    void limitIsScopedToOneIp() throws Exception {
        String busy = "203.0.113.20";
        verify("rl-a@example.test", busy);
        verify("rl-b@example.test", busy);
        verify("rl-c@example.test", busy);
        assertThat(verify("rl-d@example.test", busy)).isEqualTo(409);

        assertThat(verify("rl-elsewhere@example.test", "203.0.113.21"))
                .as("a different IP has its own quota")
                .isEqualTo(200);
    }
}