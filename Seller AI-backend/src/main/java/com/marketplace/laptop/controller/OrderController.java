package com.marketplace.laptop.controller;

import com.marketplace.common.ApiResponse;
import com.marketplace.laptop.dto.CreateOrderRequest;
import com.marketplace.laptop.dto.OrderDto;
import com.marketplace.laptop.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Tag(name = "orders", description = "Closing the sale: stock is held, price is re-derived, offer is redeemed")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/{id}/refresh-payment")
    @Operation(summary = "Ask Razorpay whether this order's payment link has been paid, and settle "
            + "it if so. For demos and local development, where the webhook has no public URL to "
            + "arrive at. Safe to call repeatedly.")
    public ApiResponse<OrderDto> refreshPayment(@PathVariable UUID id) {
        return ApiResponse.ok(service.refreshPaymentStatus(id));
    }

    @PostMapping
    @Operation(summary = "Create an order. Validates identity, stock and discount offer before pricing.")
    public ResponseEntity<ApiResponse<OrderDto>> create(@Valid @RequestBody CreateOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(req)));
    }

    @PostMapping("/{id}/payment-link")
    @Operation(summary = "Generate a payment link for an order")
    public ApiResponse<OrderDto> paymentLink(@PathVariable UUID id) {
        return ApiResponse.ok(service.createPaymentLink(id));
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Mark an order paid or failed (webhook / demo hook). A failure releases the held unit.")
    public ApiResponse<OrderDto> settle(@PathVariable UUID id,
                                        @RequestParam(defaultValue = "true") boolean paid) {
        return ApiResponse.ok(service.settle(id, paid));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Order status")
    public ApiResponse<OrderDto> status(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping
    @Operation(summary = "Orders for one identity")
    public ApiResponse<List<OrderDto>> byIdentity(@RequestParam String identityKey) {
        return ApiResponse.ok(service.byIdentity(identityKey));
    }

    @GetMapping("/payment-provider")
    @Operation(summary = "Which payment gateway implementation is wired in")
    public ApiResponse<Map<String, String>> provider() {
        return ApiResponse.ok(Map.of("provider", service.gatewayName()));
    }
}
