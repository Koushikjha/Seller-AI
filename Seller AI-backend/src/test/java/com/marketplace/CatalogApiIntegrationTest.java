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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private JsonNode data(String url) throws Exception {
        String body = mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(body);
        assertThat(root.get("ok").asBoolean()).isTrue();
        return root.get("data");
    }

    @Test
    @DisplayName("seed data loads with brands, sub-brands and laptops")
    void seedLoaded() throws Exception {
        assertThat(data("/brands")).hasSizeGreaterThanOrEqualTo(5);
        assertThat(data("/sub-brands")).hasSizeGreaterThanOrEqualTo(10);
        assertThat(data("/laptops")).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("search excludes out-of-stock models by default")
    void searchHidesOutOfStock() throws Exception {
        JsonNode inStock = data("/laptops/search?limit=25");
        inStock.forEach(l -> assertThat(l.get("stockQty").asInt()).isGreaterThan(0));

        JsonNode all = data("/laptops/search?limit=25&inStockOnly=false");
        assertThat(all.size()).isGreaterThan(inStock.size());
    }

    @Test
    @DisplayName("price and RAM filters actually filter")
    void filtersApply() throws Exception {
        JsonNode budget = data("/laptops/search?maxPrice=60000&limit=25");
        assertThat(budget).isNotEmpty();
        budget.forEach(l -> assertThat(l.get("basePrice").asDouble()).isLessThanOrEqualTo(60000.0));

        JsonNode bigRam = data("/laptops/search?minRam=16&limit=25");
        bigRam.forEach(l -> assertThat(l.get("ramGb").asInt()).isGreaterThanOrEqualTo(16));
    }

    @Test
    @DisplayName("discreteGpuRequired excludes integrated-graphics machines")
    void discreteGpuFilter() throws Exception {
        JsonNode gaming = data("/laptops/search?discreteGpuRequired=true&limit=25");
        assertThat(gaming).isNotEmpty();
        gaming.forEach(l -> {
            assertThat(l.has("gpu")).isTrue();
            assertThat(l.get("gpu").get("integrated").asBoolean()).isFalse();
        });
    }

    @Test
    @DisplayName("a model the shop does not carry returns nothing rather than something close")
    void unknownModelReturnsEmpty() throws Exception {
        assertThat(data("/laptops/search?modelNameContains=MacBook&limit=25")).isEmpty();
    }

    @Test
    @DisplayName("compare returns aligned rows with differences flagged first")
    void compareAlignsRows() throws Exception {
        JsonNode results = data("/laptops/search?limit=2");
        String a = results.get(0).get("id").asText();
        String b = results.get(1).get("id").asText();

        String body = mvc.perform(post("/laptops/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = mapper.readTree(body).get("data");
        assertThat(data.get("laptops")).hasSize(2);
        assertThat(data.get("rows")).isNotEmpty();
        data.get("rows").forEach(r -> assertThat(r.get("values")).hasSize(2));
        assertThat(data.get("rows").get(0).get("differing").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("an unknown extraSpecs key is rejected with the allowed list")
    void unknownSpecKeyRejected() throws Exception {
        JsonNode subBrand = data("/sub-brands").get(0);
        JsonNode cpu = data("/cpus").get(0);

        String payload = """
                {"subBrandId":"%s","cpuId":"%s","modelName":"Test Model","basePrice":50000,
                 "stockQty":1,"ramGb":8,"storageGb":512,
                 "extraSpecs":{"MAGIC_UNICORN_MODE":true}}
                """.formatted(subBrand.get("id").asText(), cpu.get("id").asText());

        mvc.perform(post("/laptops").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("MAGIC_UNICORN_MODE")));
    }

    @Test
    @DisplayName("an extraSpecs value of the wrong type is rejected")
    void wrongSpecTypeRejected() throws Exception {
        JsonNode subBrand = data("/sub-brands").get(0);
        JsonNode cpu = data("/cpus").get(0);

        String payload = """
                {"subBrandId":"%s","cpuId":"%s","modelName":"Test Model 2","basePrice":50000,
                 "stockQty":1,"ramGb":8,"storageGb":512,
                 "extraSpecs":{"KEYBOARD_BACKLIGHT":"yes please"}}
                """.formatted(subBrand.get("id").asText(), cpu.get("id").asText());

        mvc.perform(post("/laptops").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the generic catalog endpoint serves both device types")
    void genericCatalogCoversBothDeviceTypes() throws Exception {
        assertThat(data("/catalog/search?deviceType=LAPTOP&limit=5")).isNotEmpty();
        assertThat(data("/catalog/search?deviceType=SMARTPHONE&limit=5")).isNotEmpty();
        assertThat(data("/catalog/device-types")).hasSize(2);
    }
}
