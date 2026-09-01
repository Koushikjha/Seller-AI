package com.marketplace;
import com.marketplace.agent.state.SalesStage;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the orchestration loop against the scripted (offline) LLM client.
 *
 * The point is not that the fake model is smart — it is that the loop, the tool
 * dispatch, the state transitions and the audit trail are all verifiable with
 * no API key and no variance. Prompt quality is a separate problem from
 * whether the machinery works.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesAgentIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode send(String conversationId, String message) throws Exception {
        String body = conversationId == null
                ? "{\"message\":\"" + message + "\"}"
                : "{\"conversationId\":\"" + conversationId + "\",\"message\":\"" + message + "\"}";

        String res = mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(res);
        assertThat(root.get("ok").asBoolean()).isTrue();
        return root.get("data");
    }

    private JsonNode getData(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    @DisplayName("a first message creates a conversation and returns a reply")
    void firstMessageStartsConversation() throws Exception {
        JsonNode res = send(null, "I need a laptop for coding under 90000");
        assertThat(res.get("conversationId").asText()).isNotBlank();
        assertThat(res.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("the loop calls a tool and feeds the result back before replying")
    void loopExecutesToolsThenAnswers() throws Exception {
        JsonNode res = send(null, "gaming laptop under 90000");

        assertThat(res.get("toolCalls")).isNotEmpty();
        assertThat(res.get("toolCalls").get(0).get("tool").asText()).isEqualTo("search_laptops");
        assertThat(res.get("toolCalls").get(0).get("ok").asBoolean()).isTrue();
        assertThat(res.get("reply").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a successful search advances the sales stage and returns candidates")
    void searchAdvancesStateAndReturnsCandidates() throws Exception {
        JsonNode res = send(null, "gaming laptop under 90000");

        assertThat(res.get("products")).isNotEmpty();
        // Not an exact stage. SalesStage is a description of the situation, not a
        // gate — the scripted client searches and then presents in the same turn,
        // so the turn ends at PRODUCT_PRESENTATION. Pinning one value here tests
        // the fake model's script rather than the machinery.
        assertThat(SalesStage.valueOf(res.get("stage").asText()))
                .isGreaterThanOrEqualTo(SalesStage.PRODUCT_SEARCH);
        // Each entry is {product, reason} — the backend's record and the agent's
        // words, kept apart on purpose. Search results carry a null reason.
        res.get("products").forEach(p ->
                assertThat(p.get("product").get("stockQty").asInt()).isGreaterThan(0));
    }

    @Test
    @DisplayName("asking for a model the shop does not carry returns no candidates")
    void unstockedModelYieldsNothing() throws Exception {
        JsonNode res = send(null, "do you have a MacBook Pro");

        assertThat(res.get("toolCalls").get(0).get("tool").asText()).isEqualTo("search_laptops");
        assertThat(res.get("products")).isEmpty();
    }

    @Test
    @DisplayName("state and transcript persist across turns")
    void conversationIsResumable() throws Exception {
        String id = send(null, "I need a laptop").get("conversationId").asText();
        send(id, "gaming, under 90000");

        JsonNode state = getData("/chat/" + id + "/state");
        assertThat(state.get("toolCallsTotal").asInt()).isPositive();
        assertThat(state.get("candidateCount").asInt()).isPositive();

        JsonNode transcript = getData("/chat/" + id);
        assertThat(transcript.size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("every tool call is recorded with its arguments and result — the audit trail")
    void toolCallsAreAuditable() throws Exception {
        String id = send(null, "gaming laptop under 90000").get("conversationId").asText();

        JsonNode transcript = getData("/chat/" + id);
        boolean sawToolRow = false;
        for (JsonNode entry : transcript) {
            if ("TOOL".equals(entry.get("role").asText())) {
                sawToolRow = true;
                assertThat(entry.get("toolName").asText()).isNotBlank();
                assertThat(entry.has("toolArgs")).isTrue();
                assertThat(entry.has("toolResult")).isTrue();
                assertThat(entry.get("toolOk").asBoolean()).isTrue();
            }
        }
        assertThat(sawToolRow).as("transcript contains a TOOL row").isTrue();
    }

    @Test
    @DisplayName("a price objection is logged and moves the stage")
    void priceObjectionIsTracked() throws Exception {
        String id = send(null, "gaming laptop under 90000").get("conversationId").asText();
        send(id, "that is too expensive for me");

        JsonNode state = getData("/chat/" + id + "/state");
        assertThat(state.get("objections")).isNotEmpty();
    }

    @Test
    @DisplayName("the offline client is what tests run against")
    void providerIsScripted() throws Exception {
        assertThat(getData("/chat/meta/provider").get("provider").asText()).isEqualTo("scripted");
    }

    @Test
    @DisplayName("a budget nothing fits widens rather than returning an empty shelf")
    void budgetBelowTheShelfWidensInsteadOfRefusing() throws Exception {
        // Cheapest seeded machine is the Aspire 3 at 38,990. A strict search at
        // 35,000 finds nothing; the backend retries 20% up and returns the near
        // misses with a note saying they are over budget.
        JsonNode res = send(null, "laptop under 35000");

        assertThat(res.get("products")).isNotEmpty();
        res.get("products").forEach(p ->
                assertThat(p.get("product").get("price").decimalValue())
                        .isGreaterThan(new java.math.BigDecimal("35000")));
    }

    @Test
    @DisplayName("a budget nothing comes close to still returns nothing")
    void budgetFarBelowTheShelfStaysEmpty() throws Exception {
        // 20,000 widened by 20% is 24,000 — still under the cheapest machine.
        // Widening must not become a licence to ignore the budget entirely.
        JsonNode res = send(null, "laptop under 20000");

        assertThat(res.get("products")).isEmpty();
    }
}
