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
    /**
     * A result the agent needs a sentence of context about — "these are above the
     * ceiling you asked for", "this was widened".
     *
     * The note rides in the payload next to the data, so it is persisted in the
     * tool_result row and replayed on later turns exactly like the data is. A
     * caveat the agent is told once and then loses on the next history rebuild is
     * worse than no caveat, because it will keep quoting the result without it.
     */
    public static ToolOutcome ok(Object data, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("data", data);
        if (note != null && !note.isBlank()) m.put("note", note);
        return new ToolOutcome(true, m);
    }

}
