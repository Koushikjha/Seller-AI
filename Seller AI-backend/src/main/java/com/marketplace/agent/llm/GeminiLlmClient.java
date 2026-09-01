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
 * Gemini generateContent, spoken in the provider-neutral types.
 *
 * Kept deliberately explicit rather than hidden behind an agent framework:
 * the request/response shape here is the thing you will be asked to explain,
 * and it is the thing you will need to debug when the model does something
 * surprising.
 */
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final AgentProperties.Gemini cfg;
    private final ObjectMapper mapper;
    private final RestClient http;

    public GeminiLlmClient(AgentProperties.Gemini cfg, ObjectMapper mapper) {
        this.cfg = cfg;
        this.mapper = mapper;
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(cfg.getTimeoutSeconds()));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "gemini:" + cfg.getModel();
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<LlmMessage> history, List<ToolDefinition> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", history.stream().map(this::toContent).toList());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", List.of(Map.of("functionDeclarations", tools.stream()
                    .map(this::toFunctionDeclaration).toList())));
        }
        body.put("generationConfig", Map.of("temperature", cfg.getTemperature()));

        String url = cfg.getBaseUrl() + "/models/" + cfg.getModel() + ":generateContent";

        JsonNode response;
        try {
            response = LlmRetry.execute("Gemini", log, () -> http.post()
                    .uri(url)
                    .header("x-goog-api-key", cfg.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Gemini call failed: " + e.getMessage(), e);
        }

        return parse(response);
    }

    // ------------------------------------------------------------------

    private Map<String, Object> toFunctionDeclaration(ToolDefinition tool) {
        Map<String, Object> decl = new LinkedHashMap<>();
        decl.put("name", tool.name());
        decl.put("description", tool.description());
        decl.put("parameters", GeminiSchemaAdapter.sanitizeSchema(tool.inputSchema()));
        return decl;
    }

    private Map<String, Object> toContent(LlmMessage m) {
        return switch (m.role()) {
            case USER -> Map.of("role", "user", "parts", List.of(Map.of("text", m.text())));
            case MODEL -> {
                if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                    yield Map.of("role", "model", "parts",
                            m.toolCalls().stream().map(this::toFunctionCallPart).toList());
                }
                yield Map.of("role", "model", "parts", List.of(Map.of("text", m.text() == null ? "" : m.text())));
            }
            // Gemini carries tool results on the user turn as functionResponse
            // parts, paired by function name — toolCallId is unused here.
            case TOOL -> Map.of("role", "user", "parts", List.of(Map.of("functionResponse",
                    Map.of("name", m.toolName(), "response", m.toolResult()))));
        };
    }

    /**
     * The signature rides alongside functionCall, not inside it, and must be
     * echoed back verbatim. Parallel calls only carry one on the first part,
     * so a null signature here is normal and must not be faked.
     */
    private Object toFunctionCallPart(LlmToolCall tc) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("functionCall", Map.of("name", tc.name(), "args", tc.arguments()));
        if (tc.signature() != null && !tc.signature().isBlank()) {
            part.put("thoughtSignature", tc.signature());
        }
        return part;
    }

    private LlmResponse parse(JsonNode response) {
        if (response == null) {
            throw new LlmException("Gemini returned an empty body");
        }
        if (response.has("error")) {
            throw new LlmException("Gemini error: " + response.get("error").toString());
        }
        JsonNode candidates = response.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String reason = response.path("promptFeedback").path("blockReason").asText("");
            throw new LlmException("Gemini returned no candidates"
                    + (reason.isBlank() ? "" : " (blocked: " + reason + ")"));
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        List<LlmToolCall> calls = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        for (JsonNode part : parts) {
            if (part.has("functionCall")) {
                JsonNode fc = part.get("functionCall");
                Map<String, Object> args = fc.has("args")
                        ? mapper.convertValue(fc.get("args"), Map.class)
                        : Map.of();
                String signature = part.hasNonNull("thoughtSignature")
                        ? part.get("thoughtSignature").asText() : null;
                calls.add(new LlmToolCall(null, fc.path("name").asText(), args, signature));
            } else if (part.has("text")) {
                text.append(part.get("text").asText());
            }
        }

        if (!calls.isEmpty()) {
            log.debug("Gemini requested {} tool call(s)", calls.size());
            return LlmResponse.tools(calls);
        }
        return LlmResponse.text(text.toString());
    }
}