package com.marketplace.catalog.controller;

import com.marketplace.catalog.dto.*;
import com.marketplace.catalog.service.CatalogService;
import com.marketplace.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@Tag(name = "catalog", description = "Shared reference data: brands, sub-brands, processors, GPUs")
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/brands")
    @Operation(summary = "List every brand the shop carries")
    public ApiResponse<List<BrandDto>> brands() {
        return ApiResponse.ok(service.listBrands());
    }

    @GetMapping("/sub-brands")
    @Operation(summary = "List product lines, optionally filtered to one brand")
    public ApiResponse<List<SubBrandDto>> subBrands(@RequestParam(required = false) UUID brandId) {
        return ApiResponse.ok(service.listSubBrands(brandId));
    }

    @GetMapping("/cpus")
    public ApiResponse<List<CpuDto>> cpus() {
        return ApiResponse.ok(service.listCpus());
    }

    @GetMapping("/gpus")
    public ApiResponse<List<GpuDto>> gpus() {
        return ApiResponse.ok(service.listGpus());
    }
}
