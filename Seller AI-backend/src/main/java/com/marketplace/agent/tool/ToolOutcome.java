package com.marketplace.agent.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/** Result of one tool call, in the same shape the HTTP API returns. */
public record ToolOutcome(boolean ok, Map<String, Object> payload) {

    public static ToolOutcome ok(Object data) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("data", data);
        return new ToolOutcome(true, m);
    }

    public static ToolOutcome error(String code, String message, Object details) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        if (details != null) err.put("details", details);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", err);
        return new ToolOutcome(false, m);
    }
}
