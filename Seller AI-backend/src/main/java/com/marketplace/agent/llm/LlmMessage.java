package com.marketplace.agent.llm;

import java.util.List;
import java.util.Map;

/**
 * Provider-neutral conversation turn. Each LlmClient translates this into
 * whatever wire format its API wants, so the orchestrator never knows which
 * model it is talking to.
 *
 * {@code toolCallId} pairs a tool result back to the call that requested it.
 * OpenAI-compatible APIs (Groq) require it; Gemini pairs by name and ignores it.
 */
public record LlmMessage(
        Role role,
        String text,
        List<LlmToolCall> toolCalls,
        String toolCallId,
        String toolName,
        Map<String, Object> toolResult
) {
    public enum Role { USER, MODEL, TOOL }

    public static LlmMessage user(String text) {
        return new LlmMessage(Role.USER, text, List.of(), null, null, null);
    }

    public static LlmMessage model(String text) {
        return new LlmMessage(Role.MODEL, text, List.of(), null, null, null);
    }

    public static LlmMessage modelToolCalls(List<LlmToolCall> calls) {
        return new LlmMessage(Role.MODEL, null, calls, null, null, null);
    }

    public static LlmMessage toolResult(String toolCallId, String toolName, Map<String, Object> result) {
        return new LlmMessage(Role.TOOL, null, List.of(), toolCallId, toolName, result);
    }
}