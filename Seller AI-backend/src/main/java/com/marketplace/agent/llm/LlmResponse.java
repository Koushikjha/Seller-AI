package com.marketplace.agent.llm;

import java.util.List;

public record LlmResponse(String text, List<LlmToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static LlmResponse text(String text) {
        return new LlmResponse(text, List.of());
    }

    public static LlmResponse tools(List<LlmToolCall> calls) {
        return new LlmResponse(null, calls);
    }
}
