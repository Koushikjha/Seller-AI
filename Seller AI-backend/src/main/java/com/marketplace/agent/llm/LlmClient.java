package com.marketplace.agent.llm;

import com.marketplace.agent.ToolDefinition;

import java.util.List;

/**
 * The only seam between the sales loop and a model vendor. Swapping Gemini
 * for Claude or GPT is one implementation of this interface and a config
 * value — nothing in the orchestrator, the tool layer or the prompt changes.
 */
public interface LlmClient {

    String name();

    LlmResponse complete(String systemPrompt, List<LlmMessage> history, List<ToolDefinition> tools);
}
