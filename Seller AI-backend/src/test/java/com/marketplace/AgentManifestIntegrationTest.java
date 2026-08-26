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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentManifestIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode data(String url) throws Exception {
        return mapper.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    @DisplayName("every tool has a name, an object schema and a truth level")
    void toolsAreWellFormed() throws Exception {
        JsonNode tools = data("/agent/tools");
        assertThat(tools).isNotEmpty();
        List<String> names = new ArrayList<>();
        tools.forEach(t -> {
            assertThat(t.get("name").asText()).isNotBlank();
            assertThat(t.get("description").asText()).isNotBlank();
            assertThat(t.get("inputSchema").get("type").asText()).isEqualTo("object");
            assertThat(t.get("truthLevel").asText())
                    .isIn("AUTHORITATIVE", "UNVERIFIED_GENERAL");
            names.add(t.get("name").asText());
        });
        assertThat(names).contains("search_laptops", "request_discount", "create_order",
                "compare_laptops", "verify_identity");
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("web-sourced info is the only tool not marked authoritative")
    void onlyWebInfoIsUnverified() throws Exception {
        JsonNode tools = data("/agent/tools");
        tools.forEach(t -> {
            if (t.get("truthLevel").asText().equals("UNVERIFIED_GENERAL")) {
                assertThat(t.get("name").asText()).isEqualTo("get_product_line_info");
            }
        });
    }

    @Test
    @DisplayName("vocabulary is read from live data, not hard-coded")
    void vocabularyReflectsSeededCatalog() throws Exception {
        JsonNode v = data("/agent/vocabulary");
        assertThat(v.get("brands")).isNotEmpty();
        assertThat(v.get("segments")).isNotEmpty();
        assertThat(v.get("deviceTypes")).hasSize(2);
        assertThat(v.get("extraSpecKeysByDeviceType").has("LAPTOP")).isTrue();
        assertThat(v.get("extraSpecKeysByDeviceType").has("SMARTPHONE")).isTrue();
    }

    @Test
    @DisplayName("the manifest states the info-source boundary the prompt has to enforce")
    void manifestCarriesTheBoundary() throws Exception {
        JsonNode m = data("/agent/manifest");
        assertThat(m.get("principle").asText()).isNotBlank();
        assertThat(m.get("infoSourceBoundary").get("backendOnly")).isNotEmpty();
        assertThat(m.get("infoSourceBoundary").get("webSearchable")).isNotEmpty();
        assertThat(m.get("negotiationPolicy").get("maxRoundsCounted").asInt()).isPositive();
    }
}
