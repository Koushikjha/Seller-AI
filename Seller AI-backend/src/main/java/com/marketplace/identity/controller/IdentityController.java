package com.marketplace.identity.controller;

import com.marketplace.common.ApiResponse;
import com.marketplace.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/identity")
@Tag(name = "identity", description = "Identity verification — the key discounts and orders are bound to")
public class IdentityController {

    private final IdentityService service;

    public IdentityController(IdentityService service) {
        this.service = service;
    }

    public record VerifyRequest(@NotBlank String identityKey) {}

    @PostMapping("/verify")
    @Operation(summary = "Verify an identity (stubbed OTP), rate-limited per IP")
    public ApiResponse<Map<String, Object>> verify(@Valid @RequestBody VerifyRequest req,
                                                   HttpServletRequest http) {
        var vi = service.verify(req.identityKey(), clientIp(http));
        return ApiResponse.ok(Map.of(
                "identityKey", vi.getIdentityKey(),
                "verified", true,
                "verifiedAt", vi.getVerifiedAt()));
    }

    @GetMapping("/status")
    @Operation(summary = "Check whether an identity is already verified")
    public ApiResponse<Map<String, Object>> status(@RequestParam String identityKey) {
        return ApiResponse.ok(Map.of(
                "identityKey", identityKey,
                "verified", service.isVerified(identityKey),
                "checkedAt", Instant.now()));
    }

    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
