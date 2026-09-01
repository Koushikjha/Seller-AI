package com.marketplace.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.agent.ToolDefinition;
import com.marketplace.config.AgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * Groq, via its OpenAI-compatible chat/completions API.
 *
 * Three differences from Gemini that the adapter absorbs so nothing above this
 * class has to care:
 *
 *   1. Tool arguments travel as a JSON *string*, not an object — parsed on the
 *      way in, re-serialised on the way out.
 *   2. Every tool call carries an {@code id}, and the result message must quote
 *      it back as {@code tool_call_id}. Gemini pairs by function name instead.
 *   3. No thought signatures, and the schema is full JSON Schema — so unlike
 *      Gemini, the tool definitions go across untouched.
 */
public class GroqLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);

    private final AgentProperties.Groq cfg;
    private final ObjectMapper mapper;
    private final RestClient http;

    public GroqLlmClient(AgentProperties.Groq cfg, ObjectMapper mapper) {
        this.cfg = cfg;
        this.mapper = mapper;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(cfg.getTimeoutSeconds()));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "groq:" + cfg.getModel();
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<LlmMessage> history, List<ToolDefinition> tools) {
        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        history.forEach(m -> messages.add(toMessage(m)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getModel());
        body.put("messages", messages);
        body.put("temperature", cfg.getTemperature());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(this::toToolSpec).toList());
            body.put("tool_choice", "auto");
        }

        JsonNode response;
        try {
            response = LlmRetry.execute("Groq", log, () -> http.post()
                    .uri(cfg.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Groq call failed: " + e.getMessage(), e);
        }

        return parse(response);
    }

    // ------------------------------------------------------------------

    private Map<String, Object> toToolSpec(ToolDefinition tool) {
        return Map.of("type", "function", "function", Map.of(
                "name", tool.name(),
                "description", tool.description(),
                "parameters", tool.inputSchema()));
    }

    private Object toMessage(LlmMessage m) {
        return switch (m.role()) {
            case USER -> Map.of("role", "user", "content", m.text() == null ? "" : m.text());

            case MODEL -> {
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", "assistant");
                    msg.put("content", null);
                    msg.put("tool_calls", m.toolCalls().stream().map(this::toToolCall).toList());
                    yield msg;
                }
                yield Map.of("role", "assistant", "content", m.text() == null ? "" : m.text());
            }

            case TOOL -> {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", "tool");
                // Falls back to the tool name when no id was stored — some
                // providers tolerate it, and it beats sending null.
                msg.put("tool_call_id", m.toolCallId() == null ? m.toolName() : m.toolCallId());
                msg.put("name", m.toolName());
                msg.put("content", writeJson(m.toolResult()));
                yield msg;
            }
        };
    }

    private Map<String, Object> toToolCall(LlmToolCall tc) {
        return Map.of(
                "id", tc.id() == null ? tc.name() : tc.id(),
                "type", "function",
                "function", Map.of(
                        "name", tc.name(),
                        "arguments", writeJson(tc.arguments())));
    }

    private LlmResponse parse(JsonNode response) {
        if (response == null) {
            throw new LlmException("Groq returned an empty body");
        }
        if (response.has("error")) {
            throw new LlmException("Groq error: " + response.get("error").toString());
        }
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmException("Groq returned no choices");
        }

        JsonNode message = choices.get(0).path("message");
        JsonNode toolCalls = message.path("tool_calls");

        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            List<LlmToolCall> calls = new ArrayList<>();
            for (JsonNode tc : toolCalls) {
                JsonNode fn = tc.path("function");
                calls.add(new LlmToolCall(
                        tc.path("id").asText(null),
                        fn.path("name").asText(),
                        readArguments(fn.path("arguments")),
                        null));   // Groq has no thought signatures
            }
            log.debug("Groq requested {} tool call(s)", calls.size());
            return LlmResponse.tools(calls);
        }

        return LlmResponse.text(message.path("content").asText(""));
    }

    /** Arguments arrive as a JSON string. A malformed one is the model's fault, not a crash. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readArguments(JsonNode arguments) {
        if (arguments.isMissingNode() || arguments.isNull()) {
            return Map.of();
        }
        try {
            if (arguments.isObject()) {
                return mapper.convertValue(arguments, Map.class);
            }
            String raw = arguments.asText("");
            return raw.isBlank() ? Map.of() : mapper.readValue(raw, Map.class);
        } catch (Exception e) {
            log.warn("Groq returned unparseable tool arguments: {}", arguments, e);
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new LlmException("Could not serialise payload for Groq", e);
        }
    }
}