package com.marketplace.agent.llm;

import com.marketplace.agent.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic stand-in for a real model.
 *
 * It is not intelligent and is not trying to be — its job is to exercise the
 * orchestration loop (model -> tool call -> tool result -> model -> text) with
 * no API key, no network, and no variance. That makes the loop, the tool
 * dispatch, the state updates and the persistence all testable on their own,
 * before any of it is entangled with prompt quality.
 *
 * Switch to Gemini with marketplace.agent.provider=gemini.
 */
public class ScriptedLlmClient implements LlmClient {

    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]{3,})");

    @Override
    public String name() {
        return "scripted";
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<LlmMessage> history, List<ToolDefinition> tools) {
        if (history.isEmpty()) {
            return LlmResponse.text("Hi — what are you looking for today?");
        }

        LlmMessage last = history.get(history.size() - 1);

        // A tool just returned: summarise and stop. This is the loop's exit.
        if (last.role() == LlmMessage.Role.TOOL) {
            Object results = last.toolResult() == null ? null : last.toolResult().get("data");
            int count = results instanceof List<?> list ? list.size() : (results == null ? 0 : 1);
            if (count == 0) {
                return LlmResponse.text("I don't have anything matching that in stock right now. "
                        + "Tell me a bit more about what you need and I'll find the closest fit.");
            }
            return LlmResponse.text("I found " + count + " option"
                    + (count == 1 ? "" : "s") + " that fit. Want me to walk through them?");
        }

        String text = last.text() == null ? "" : last.text().toLowerCase();

        // Anything that reads like a product request goes to inventory. Note it
        // searches even for a MacBook: the shop's answer comes from the catalog,
        // never from the model's assumptions about what exists.
        Map<String, Object> args = new LinkedHashMap<>();
        Matcher m = AMOUNT.matcher(text.replace(",", ""));
        if (m.find()) {
            args.put("maxPrice", Integer.parseInt(m.group(1)));
        }
        if (text.contains("gaming") || text.contains("game")) {
            args.put("discreteGpuRequired", true);
        }
        if (text.contains("light") || text.contains("travel") || text.contains("portable")) {
            args.put("maxWeightKg", 1.6);
        }
        for (String brand : List.of("macbook", "asus", "lenovo", "hp", "dell", "acer")) {
            if (text.contains(brand)) {
                args.put("modelNameContains", brand);
                break;
            }
        }
        args.put("limit", 5);

        return LlmResponse.tools(List.of(LlmToolCall.of("search_laptops", args)));
    }
}