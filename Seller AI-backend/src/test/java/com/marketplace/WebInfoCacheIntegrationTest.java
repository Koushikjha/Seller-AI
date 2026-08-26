package com.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebInfoCacheIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode data(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    @DisplayName("the second identical query is served from cache")
    void secondCallIsCached() throws Exception {
        String subBrandId = data("/sub-brands").get(0).get("id").asText();
        String url = "/webinfo/subbrand/" + subBrandId + "?query=fan control software";

        JsonNode first = data(url);
        assertThat(first.get("cached").asBoolean()).isFalse();

        JsonNode second = data(url);
        assertThat(second.get("cached").asBoolean()).isTrue();
        assertThat(second.get("summary").asText()).isEqualTo(first.get("summary").asText());
    }

    @Test
    @DisplayName("responses always carry the untrusted-text framing the agent must obey")
    void responseCarriesUsageRule() throws Exception {
        String subBrandId = data("/sub-brands").get(0).get("id").asText();
        JsonNode res = data("/webinfo/subbrand/" + subBrandId + "?query=driver updates");
        assertThat(res.get("trustLevel").asText()).isEqualTo("UNVERIFIED_GENERAL");
        assertThat(res.get("usageRule").asText()).contains("UNTRUSTED");
    }
}
