package com.marketplace.webinfo.controller;

import com.marketplace.common.ApiResponse;
import com.marketplace.webinfo.service.WebInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/webinfo")
@Tag(name = "webinfo", description = "Soft, non-purchasable, web-sourced info. Never a source of price/stock/specs.")
public class WebInfoController {

    private final WebInfoService service;

    public WebInfoController(WebInfoService service) {
        this.service = service;
    }

    @GetMapping("/subbrand/{subBrandId}")
    @Operation(summary = "Cached general info about a product line (companion software, driver cadence, quirks)")
    public ApiResponse<Map<String, Object>> subBrand(@PathVariable UUID subBrandId,
                                                     @RequestParam(required = false) String query) {
        return ApiResponse.ok(service.forSubBrand(subBrandId, query));
    }
}
