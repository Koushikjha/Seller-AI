package com.marketplace.agent;

import java.util.List;
import java.util.Map;

/**
 * One tool, in the shape function-calling APIs expect.
 *
 * {@code inputSchema} is a JSON Schema object. {@code httpMethod}/{@code path}
 * tell your agent runtime how to actually call it, so wiring a new tool is a
 * data change here rather than hand-written glue in the agent service.
 */
public record ToolDefinition(
        String name,
        String description,
        String httpMethod,
        String path,
        Map<String, Object> inputSchema,
        List<String> failureCodes,
        String truthLevel
) {
    /** Backend-owned fact. Safe to state to the customer verbatim. */
    public static final String AUTHORITATIVE = "AUTHORITATIVE";
    /** Soft/web-sourced. Must be framed as general and unverified. */
    public static final String UNVERIFIED = "UNVERIFIED_GENERAL";
}
