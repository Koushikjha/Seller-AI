package com.marketplace.laptop.controller;

import com.marketplace.common.ApiResponse;
import com.marketplace.laptop.dto.*;
import com.marketplace.laptop.service.LaptopCompareService;
import com.marketplace.laptop.service.LaptopSearchService;
import com.marketplace.laptop.service.LaptopService;
import com.marketplace.laptop.spec.ExtraSpecKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/laptops")
@Tag(name = "laptops", description = "Laptop catalog: merchant CRUD plus the agent's search and compare tools")
public class LaptopController {

    private final LaptopService service;
    private final LaptopSearchService searchService;
    private final LaptopCompareService compareService;

    public LaptopController(LaptopService service, LaptopSearchService searchService,
                            LaptopCompareService compareService) {
        this.service = service;
        this.searchService = searchService;
        this.compareService = compareService;
    }

    // ---------- merchant ----------

    @PostMapping
    @Operation(summary = "Create a laptop (merchant)")
    public ResponseEntity<ApiResponse<LaptopDto>> create(@Valid @RequestBody LaptopUpsertRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a laptop (merchant)")
    public ApiResponse<LaptopDto> update(@PathVariable UUID id, @Valid @RequestBody LaptopUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PatchMapping("/{id}/stock")
    @Operation(summary = "Adjust stock only (merchant)")
    public ApiResponse<LaptopDto> updateStock(@PathVariable UUID id, @Valid @RequestBody StockUpdateRequest req) {
        return ApiResponse.ok(service.updateStock(id, req.stockQty()));
    }

    @PatchMapping("/{id}/discount")
    @Operation(summary = "Set the negotiation ceiling for one laptop (merchant)")
    public ApiResponse<LaptopDto> updateDiscount(@PathVariable UUID id,
                                                 @Valid @RequestBody DiscountLimitUpdateRequest req) {
        return ApiResponse.ok(service.updateMaxDiscount(id, req.maxDiscountPct()));
    }

    @PatchMapping("/{id}/images")
    @Operation(summary = "Replace a product's photos (merchant). URLs or /images/... paths.")
    public ApiResponse<LaptopDto> updateImages(@PathVariable UUID id,
                                               @RequestBody ImagesUpdateRequest req) {
        return ApiResponse.ok(service.updateImages(id, req.images()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a laptop (merchant)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/spec-keys")
    @Operation(summary = "Allowed extraSpecs keys — drives the merchant dropdown and the agent's vocabulary")
    public ApiResponse<List<Map<String, String>>> specKeys() {
        return ApiResponse.ok(ExtraSpecKey.vocabulary().stream()
                .map(k -> Map.of(
                        "key", k.key(),
                        "type", k.type(),
                        "description", k.description() == null ? "" : k.description()))
                .toList());
    }

    // ---------- read / agent tools ----------

    @GetMapping
    @Operation(summary = "List every laptop (merchant dashboard)")
    public ApiResponse<List<LaptopDto>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full detail for one laptop")
    public ApiResponse<LaptopDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/search")
    @Operation(summary = "Filtered search. Out-of-stock rows are excluded unless inStockOnly=false.")
    public ApiResponse<List<LaptopDto>> search(
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minRam,
            @RequestParam(required = false) Integer minStorage,
            @RequestParam(required = false) String storageType,
            @RequestParam(required = false) String os,
            @RequestParam(required = false) Integer refreshRateMin,
            @RequestParam(required = false) BigDecimal maxWeightKg,
            @RequestParam(required = false) Integer minBatteryHours,
            @RequestParam(required = false) Boolean touchscreen,
            @RequestParam(required = false) String displayType,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String subBrand,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String priceTier,
            @RequestParam(required = false) String cpuBenchmarkTier,
            @RequestParam(required = false) String gpuBrand,
            @RequestParam(required = false) String gpuBenchmarkTier,
            @RequestParam(required = false) Boolean discreteGpuRequired,
            @RequestParam(required = false) Integer minVramGb,
            @RequestParam(required = false) String modelNameContains,
            @RequestParam(required = false, defaultValue = "true") boolean inStockOnly,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sort) {

        var criteria = new LaptopSearchCriteria(minPrice, maxPrice, minRam, minStorage, storageType, os,
                refreshRateMin, maxWeightKg, minBatteryHours, touchscreen, displayType, brand, subBrand,
                segment, priceTier, cpuBenchmarkTier, gpuBrand, gpuBenchmarkTier, discreteGpuRequired,
                minVramGb, modelNameContains, inStockOnly, limit, sort);

        return ApiResponse.ok(searchService.search(criteria));
    }

    @PostMapping("/compare")
    @Operation(summary = "Aligned spec table for 2-5 laptops; differing rows are flagged and sorted first")
    public ApiResponse<CompareResponse> compare(@Valid @RequestBody CompareRequest req) {
        return ApiResponse.ok(compareService.compare(req.ids()));
    }
}