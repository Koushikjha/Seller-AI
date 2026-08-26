package com.marketplace.agent.llm;

import java.util.Map;

/**
 * A tool the model wants called.
 *
 * {@code signature} is provider-opaque state attached to this specific call.
 * Gemini 3 puts a `thoughtSignature` on every functionCall part and returns a
 * 400 on the next request unless it comes back unchanged, in the same part it
 * arrived on. Treat it as a blob: never parse it, never synthesise one, never
 * move it between calls.
 */
public record LlmToolCall(String id, String name, Map<String, Object> arguments, String signature) {

    public static LlmToolCall of(String name, Map<String, Object> arguments) {
        return new LlmToolCall(null, name, arguments, null);
    }

    public LlmToolCall withSignature(String signature) {
        return new LlmToolCall(id, name, arguments, signature);
    }
}