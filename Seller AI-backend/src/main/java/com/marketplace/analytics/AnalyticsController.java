package com.marketplace.analytics;

import com.marketplace.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
@Tag(name = "analytics", description = "Merchant view: conversations, conversion, discount exposure")
public class AnalyticsController {

    private final AnalyticsService service;
    private final HallucinationAuditService audit;

    public AnalyticsController(AnalyticsService service, HallucinationAuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/conversations")
    @Operation(summary = "Every conversation, newest first")
    public ApiResponse<List<ConversationSummaryDto>> conversations() {
        return ApiResponse.ok(service.listConversations());
    }

    @GetMapping("/analytics/audit/{conversationId}")
    @Operation(summary = "Replay one transcript and check every claim against the tool results")
    public ApiResponse<Map<String, Object>> auditOne(@PathVariable UUID conversationId) {
        return ApiResponse.ok(audit.audit(conversationId));
    }

    @GetMapping("/analytics/audit")
    @Operation(summary = "Claim accuracy across every conversation — the not-a-chatbot metric")
    public ApiResponse<Map<String, Object>> auditAll() {
        return ApiResponse.ok(audit.auditAll());
    }

    @GetMapping("/analytics/summary")
    @Operation(summary = "Conversion, revenue, discount exposure and agent behaviour metrics")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(service.summary());
    }
}