package com.marketplace.laptop.controller;

import com.marketplace.common.ApiResponse;
import com.marketplace.laptop.dto.DiscountOfferDto;
import com.marketplace.laptop.dto.DiscountRequest;
import com.marketplace.laptop.service.DiscountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/discounts")
@Tag(name = "discounts", description = "Deterministic negotiation. The agent asks; this decides.")
public class DiscountController {

    private final DiscountService service;

    public DiscountController(DiscountService service) {
        this.service = service;
    }

    @GetMapping("/limit/{laptopId}")
    @Operation(summary = "Negotiation envelope for a laptop. maxPossiblePct is merchant-internal — never quote it to a customer.")
    public ApiResponse<Map<String, Object>> limit(@PathVariable UUID laptopId) {
        return ApiResponse.ok(service.limit(laptopId));
    }

    @PostMapping("/request")
    @Operation(summary = "Ask for a discount. Returns the only figure the agent may state out loud.")
    public ApiResponse<DiscountOfferDto> request(@Valid @RequestBody DiscountRequest req) {
        return ApiResponse.ok(service.request(req));
    }

    @GetMapping("/{offerId}/valid")
    @Operation(summary = "Check an offer is still usable by this identity")
    public ApiResponse<Map<String, Object>> valid(@PathVariable UUID offerId,
                                                  @RequestParam String identityKey) {
        return ApiResponse.ok(service.validity(offerId, identityKey));
    }

    @GetMapping("/history")
    @Operation(summary = "Every offer issued to an identity (merchant/abuse analysis)")
    public ApiResponse<List<DiscountOfferDto>> history(@RequestParam String identityKey) {
        return ApiResponse.ok(service.history(identityKey));
    }
}
