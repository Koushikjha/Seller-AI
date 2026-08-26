package com.marketplace.agent.dto;

import java.util.Map;

/**
 * Returned to the caller with every reply. This is deliberately visible in the
 * API: it lets the UI show which tools fired, and it is the raw material for
 * the "unauthorised claims" metric — every figure in the reply should trace to
 * one of these results.
 */
public record ToolCallView(
        String tool,
        Map<String, Object> arguments,
        boolean ok,
        String errorCode,
        Integer latencyMs
) {}
