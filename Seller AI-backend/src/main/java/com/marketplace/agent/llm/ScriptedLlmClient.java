package com.marketplace.agent.llm;

import com.marketplace.agent.ToolDefinition;
import com.marketplace.laptop.dto.LaptopSummaryDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deterministic stand-in for a real model.
 *
 * Not intelligent and not trying to be. Its job is to drive the full
 * orchestration loop — search, then present with reasons, then reply — with no
 * API key, no network and no variance, so the UI and the plumbing can both be
 * exercised for free. Prompt quality is a separate problem from whether the
 * machinery works.
 *
 * It reaches into LaptopSummaryDto, which a real provider client would never
 * do. That coupling is deliberate and contained: this class is a test double.
 *
 * Switch to a real model with marketplace.agent.provider=gemini|groq.
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

        if (last.role() == LlmMessage.Role.TOOL) {
            return afterTool(last);
        }

        return LlmResponse.tools(List.of(LlmToolCall.of("search_laptops", searchArgs(last.text()))));
    }

    // ------------------------------------------------------------------

    private LlmResponse afterTool(LlmMessage last) {
        String tool = last.toolName();
        List<Object> rows = dataAsList(last.toolResult());

        if ("search_laptops".equals(tool)) {
            if (rows.isEmpty()) {
                return LlmResponse.text("I don't have anything matching that in stock right now. "
                        + "Tell me a bit more about what you need and I'll find the closest fit.");
            }
            // Show the two cheapest, each with a reason — the same shape a real
            // model is asked to produce.
            List<Map<String, Object>> items = new ArrayList<>();
            String[] reasons = {
                    "Closest fit to what you described, at the lowest price I can do it for.",
                    "A step up if you want more headroom — worth the difference if you'll keep it a while.",
            };
            for (int i = 0; i < Math.min(2, rows.size()); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("laptopId", idOf(rows.get(i)));
                item.put("reason", reasons[i]);
                items.add(item);
            }
            return LlmResponse.tools(List.of(
                    LlmToolCall.of("present_products", Map.of("items", items))));
        }

        if ("present_products".equals(tool)) {
            return LlmResponse.text("Here's what I'd look at. Tell me which one interests you, "
                    + "or what you'd change about them.");
        }

        return LlmResponse.text("Got it. What else would you like to know?");
    }

    private Map<String, Object> searchArgs(String userText) {
        String text = userText == null ? "" : userText.toLowerCase();
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
        args.put("limit", 6);
        return args;
    }

    @SuppressWarnings("unchecked")
    private List<Object> dataAsList(Map<String, Object> payload) {
        if (payload == null) return List.of();
        Object data = payload.get("data");
        return data instanceof List<?> list ? new ArrayList<>((List<Object>) list) : List.of();
    }

    /** Rows arrive as DTOs live, or as maps once rebuilt from the database. */
    private String idOf(Object row) {
        if (row instanceof LaptopSummaryDto dto) {
            return dto.id().toString();
        }
        if (row instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id != null) return String.valueOf(id);
        }
        return "";
    }
}