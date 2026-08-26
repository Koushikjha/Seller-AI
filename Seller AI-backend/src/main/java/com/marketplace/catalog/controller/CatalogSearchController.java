package com.marketplace.catalog.controller;

import com.marketplace.catalog.core.*;
import com.marketplace.common.ApiResponse;
import com.marketplace.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Device-type-agnostic search. This is the endpoint the agent should call when
 * the store grows past laptops: one tool, one response shape, every device type.
 * Device-specific endpoints (/laptops/search) stay available for richer filters.
 */
@RestController
@RequestMapping("/catalog")
@Tag(name = "catalog-search", description = "Generic cross-device catalog access for the agent")
public class CatalogSearchController {

    /** Query params consumed by CatalogQuery itself; everything else is device-specific. */
    private static final List<String> SHARED_PARAMS = List.of(
            "deviceType", "minPrice", "maxPrice", "minRamGb", "minStorageGb", "brand",
            "segment", "priceTier", "cpuBenchmarkTier", "inStockOnly", "limit");

    private final CatalogRegistry registry;

    public CatalogSearchController(CatalogRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/device-types")
    @Operation(summary = "Device types currently sellable, with their spec vocabulary")
    public ApiResponse<List<Map<String, Object>>> deviceTypes() {
        List<Map<String, Object>> out = registry.all().stream()
                .map(p -> Map.<String, Object>of(
                        "deviceType", p.deviceType().name(),
                        "extraSpecKeys", p.specVocabulary().stream()
                                .map(k -> Map.of("key", k.key(), "type", k.type(),
                                        "description", k.description() == null ? "" : k.description()))
                                .toList(),
                        "deviceSpecificFilters", p.deviceSpecificFilters()))
                .toList();
        return ApiResponse.ok(out);
    }

    @GetMapping("/search")
    @Operation(summary = "Search any device type; only in-stock items are returned by default")
    public ApiResponse<List<CatalogItemView>> search(
            @RequestParam DeviceType deviceType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minRamGb,
            @RequestParam(required = false) Integer minStorageGb,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String priceTier,
            @RequestParam(required = false) String cpuBenchmarkTier,
            @RequestParam(required = false, defaultValue = "true") boolean inStockOnly,
            @RequestParam(required = false) Integer limit,
            @RequestParam Map<String, String> allParams) {

        Map<String, String> deviceSpecific = new HashMap<>(allParams);
        SHARED_PARAMS.forEach(deviceSpecific::remove);

        CatalogQuery query = new CatalogQuery(deviceType, minPrice, maxPrice, minRamGb, minStorageGb,
                brand, segment, priceTier, cpuBenchmarkTier, inStockOnly, limit, deviceSpecific);

        return ApiResponse.ok(registry.get(deviceType).search(query));
    }

    @GetMapping("/{deviceType}/{id}")
    @Operation(summary = "Fetch one catalog item in the generic view shape")
    public ApiResponse<CatalogItemView> byId(@PathVariable DeviceType deviceType, @PathVariable UUID id) {
        return ApiResponse.ok(registry.get(deviceType).findById(id)
                .orElseThrow(() -> new NotFoundException(deviceType.name(), id)));
    }
}
