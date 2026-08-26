package com.marketplace.agent;

import com.marketplace.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Everything the agent layer needs to configure itself, served from live data.
 * Point your agent service at /agent/manifest on startup and the prompt stops
 * being a place where merchant facts go stale.
 */
@RestController
@RequestMapping("/agent")
@Tag(name = "agent", description = "Tool definitions and live vocabulary for the LLM sales agent")
public class AgentController {

    private final AgentManifestService service;

    public AgentController(AgentManifestService service) {
        this.service = service;
    }

    @GetMapping("/tools")
    @Operation(summary = "JSON-schema tool definitions for function calling")
    public ApiResponse<List<ToolDefinition>> tools() {
        return ApiResponse.ok(service.tools());
    }

    @GetMapping("/vocabulary")
    @Operation(summary = "Live filter values — brands, segments, tiers, spec keys — read from the database")
    public ApiResponse<Map<String, Object>> vocabulary() {
        return ApiResponse.ok(service.vocabulary());
    }

    @GetMapping("/manifest")
    @Operation(summary = "Tools + vocabulary + info-source boundary + negotiation policy in one payload")
    public ApiResponse<Map<String, Object>> manifest() {
        return ApiResponse.ok(service.manifest());
    }
}
