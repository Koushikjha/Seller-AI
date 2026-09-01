package com.marketplace.agent.orchestrator;

import com.marketplace.agent.AgentManifestService;
import com.marketplace.agent.dto.ChatResponse;
import com.marketplace.agent.dto.ToolCallView;
import com.marketplace.agent.llm.*;
import com.marketplace.agent.state.*;
import com.marketplace.agent.tool.ToolExecutor;
import com.marketplace.agent.tool.ToolOutcome;
import com.marketplace.common.NotFoundException;
import com.marketplace.config.AgentProperties;
import com.marketplace.identity.repository.VerifiedIdentityRepository;
import com.marketplace.laptop.dto.DiscountOfferDto;
import com.marketplace.laptop.dto.LaptopDto;
import com.marketplace.laptop.dto.LaptopSummaryDto;
import com.marketplace.laptop.dto.PresentedProductDto;
import com.marketplace.laptop.dto.OrderDto;
import com.marketplace.laptop.repository.DiscountOfferRepository;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.laptop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * THE LOOP.
 *
 *   user message
 *     -> model
 *        -> tool call?  execute it, feed the result back, ask again
 *        -> plain text? that is the reply, stop
 *
 * Everything interesting about this project lives in the interaction between
 * this class, ToolExecutor and the Conversation row — not in a framework.
 *
 * Deliberately NOT @Transactional at method level: a turn can spend twenty
 * seconds inside an LLM call, and holding a database connection open across
 * that would exhaust the pool with three concurrent users. Each save runs in
 * its own short transaction; the tool services manage their own.
 */
@Service
public class SalesAgentService {

    private static final Logger log = LoggerFactory.getLogger(SalesAgentService.class);

    /** Cheap signal for the objection log and stage hint. Not a decision input. */
    private static final List<String> OBJECTION_MARKERS = List.of(
            "expensive", "too much", "costly", "cheaper", "discount", "budget",
            "lower price", "out of my range", "can't afford", "afford");

    private final LlmClient llm;
    private final AgentManifestService manifest;
    private final SystemPromptBuilder prompts;
    private final ToolExecutor tools;
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final LaptopRepository laptops;
    private final DiscountOfferRepository offers;
    private final OrderRepository orders;
    private final VerifiedIdentityRepository identities;
    private final AgentProperties props;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    public SalesAgentService(LlmClient llm, AgentManifestService manifest, SystemPromptBuilder prompts,
                             ToolExecutor tools, ConversationRepository conversations,
                             ConversationMessageRepository messages, LaptopRepository laptops,
                             DiscountOfferRepository offers, OrderRepository orders,
                             VerifiedIdentityRepository identities, AgentProperties props) {
        this.llm = llm;
        this.manifest = manifest;
        this.prompts = prompts;
        this.tools = tools;
        this.conversations = conversations;
        this.messages = messages;
        this.laptops = laptops;
        this.offers = offers;
        this.orders = orders;
        this.identities = identities;
        this.props = props;
    }

    public ChatResponse chat(UUID conversationId, String userText) {
        Conversation conv = conversationId == null
                ? conversations.save(new Conversation())
                : conversations.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationId));

        noteObjection(conv, userText);
        int seq = messages.maxSeq(conv.getId());
        persist(conv, ++seq, MessageRole.USER, userText, null, null, null, null, null, null);

        List<LlmMessage> history = rebuildHistory(conv.getId());
        List<ToolCallView> executed = new ArrayList<>();
        // Search results and deliberate presentations are kept apart: if the agent
        // chose what to show, that choice is what the customer sees. Concatenating
        // both means six raw hits plus the two it actually recommended.
        List<PresentedProductDto> searchResults = new ArrayList<>();
        List<PresentedProductDto> presented = new ArrayList<>();
        String reply = null;

        for (int iteration = 0; iteration < props.getMaxToolIterations(); iteration++) {
            LlmResponse response;
            try {
                response = llm.complete(prompts.build(conv), history, manifest.tools());
            } catch (LlmException e) {
                log.error("LLM call failed on conversation {}", conv.getId(), e);
                reply = "Sorry — I lost my train of thought there. Could you say that again?";
                break;
            }

            if (!response.hasToolCalls()) {
                reply = response.text();
                break;
            }

            history.add(LlmMessage.modelToolCalls(response.toolCalls()));

            for (LlmToolCall call : response.toolCalls()) {
                long startedAt = System.currentTimeMillis();
                ToolOutcome outcome = tools.execute(call.name(), call.arguments(), conv);
                int latency = (int) (System.currentTimeMillis() - startedAt);

                conv.setToolCallsTotal(conv.getToolCallsTotal() + 1);
                List<PresentedProductDto> fromTool = applyStateEffects(conv, call.name(), outcome);
                if ("present_products".equals(call.name())) {
                    presented.addAll(fromTool);
                } else {
                    searchResults.addAll(fromTool);
                }

                persist(conv, ++seq, MessageRole.TOOL, null, call.name(),
                        call.arguments(), outcome.payload(), outcome.ok(), latency,
                        toolMetaOf(call));

                executed.add(new ToolCallView(call.name(), call.arguments(), outcome.ok(),
                        outcome.ok() ? null : errorCode(outcome), latency));

                history.add(LlmMessage.toolResult(call.id(), call.name(), outcome.payload()));
            }
        }

        if (reply == null) {
            // Ran out of iterations without the model settling on an answer.
            reply = "Let me stop and check I've got this right — could you tell me again "
                    + "what matters most to you here?";
            log.warn("Conversation {} hit the {}-iteration tool cap",
                    conv.getId(), props.getMaxToolIterations());
        }

        List<PresentedProductDto> products = presented.isEmpty() ? searchResults : presented;

        if (products.isEmpty() && reply.contains("?")
                && (conv.getCandidateIds() == null || conv.getCandidateIds().isEmpty())) {
            conv.setQuestionsAsked(conv.getQuestionsAsked() + 1);
        }

        persist(conv, ++seq, MessageRole.ASSISTANT, reply, null, null, null, null, null, null);
        conversations.save(conv);

        return new ChatResponse(conv.getId(), reply, conv.getStage(), conv.identityKey() != null,
                executed, products,
                conv.getSelectedLaptop() == null ? null : conv.getSelectedLaptop().getId(),
                conv.getDiscountOffer() == null ? null : conv.getDiscountOffer().getId(),
                conv.getOrder() == null ? null : conv.getOrder().getId());
    }

    // ------------------------------------------------------------------

    /**
     * Tool results, not the model's narration, are what move the sales state.
     * The model can say it found a laptop; only search_laptops returning rows
     * actually advances the stage.
     */
    @SuppressWarnings("unchecked")
    private List<PresentedProductDto> applyStateEffects(Conversation conv, String tool, ToolOutcome outcome) {
        if (!outcome.ok()) {
            return List.of();
        }
        Object data = outcome.payload().get("data");
        List<PresentedProductDto> selected = new ArrayList<>();

        switch (tool) {
            case "search_laptops", "search_catalog" -> {
                if (data instanceof List<?> list) {
                    List<LaptopSummaryDto> found = list.stream()
                            .filter(LaptopSummaryDto.class::isInstance)
                            .map(LaptopSummaryDto.class::cast).toList();
                    if (!found.isEmpty()) {
                        conv.setCandidateIds(found.stream().map(l -> l.id().toString()).toList());
                        advance(conv, SalesStage.PRODUCT_SEARCH);
                        // Search results carry no reason — the agent has not chosen yet.
                        return found.stream()
                                .map(l -> new PresentedProductDto(l, null)).toList();
                    }
                    conv.setCandidateIds(List.of());
                }
            }
            case "get_laptop_details" -> {
                if (data instanceof LaptopDto dto) {
                    laptops.findById(dto.id())
                            .ifPresent(l -> {
                                conv.setSelectedLaptop(l);
                                selected.add(new PresentedProductDto(LaptopSummaryDto.from(l), null));
                            });
                    advance(conv, SalesStage.PRODUCT_PRESENTATION);
                    return selected;
                }
            }
            case "present_products" -> {
                if (data instanceof List<?> list) {
                    List<PresentedProductDto> shown = list.stream()
                            .filter(PresentedProductDto.class::isInstance)
                            .map(PresentedProductDto.class::cast).toList();
                    if (!shown.isEmpty()) {
                        conv.setCandidateIds(shown.stream()
                                .map(p -> p.product().id().toString()).toList());
                        advance(conv, SalesStage.PRODUCT_PRESENTATION);
                        return shown;
                    }
                }
            }
            case "compare_laptops" -> advance(conv, SalesStage.PRODUCT_PRESENTATION);
            case "get_discount_limit" -> advance(conv, SalesStage.NEGOTIATION);
            case "request_discount" -> {
                if (data instanceof DiscountOfferDto dto) {
                    offers.findById(dto.offerId()).ifPresent(conv::setDiscountOffer);
                    laptops.findById(dto.laptopId()).ifPresent(conv::setSelectedLaptop);
                }
                advance(conv, SalesStage.NEGOTIATION);
            }
            case "verify_identity" -> {
                if (data instanceof Map<?, ?> map) {
                    Object key = ((Map<String, Object>) map).get("identityKey");
                    if (key != null) {
                        identities.findById(String.valueOf(key)).ifPresent(conv::setIdentity);
                    }
                }
                advance(conv, SalesStage.CLOSING);
            }
            case "create_order" -> {
                if (data instanceof OrderDto dto) {
                    orders.findById(dto.orderId()).ifPresent(conv::setOrder);
                }
                conv.setStage(SalesStage.CHECKOUT);
            }
            case "create_payment_link", "get_order_status" -> conv.setStage(SalesStage.CHECKOUT);
            default -> { }
        }
        return List.of();
    }

    /** Never move backwards past CHECKOUT; otherwise the latest signal wins. */
    private void advance(Conversation conv, SalesStage stage) {
        if (conv.getStage() != SalesStage.CHECKOUT) {
            conv.setStage(stage);
        }
    }

    private void noteObjection(Conversation conv, String userText) {
        if (userText == null) return;
        String lower = userText.toLowerCase();
        boolean priceObjection = OBJECTION_MARKERS.stream().anyMatch(lower::contains);
        if (!priceObjection) return;

        List<String> log = new ArrayList<>(
                conv.getObjections() == null ? List.of() : conv.getObjections());
        String trimmed = userText.length() > 200 ? userText.substring(0, 200) : userText;
        if (log.size() < 20) {
            log.add(trimmed);
        }
        conv.setObjections(log);
        advance(conv, SalesStage.OBJECTION_HANDLING);
    }

    /**
     * Rebuilds the conversation for the model, compacting old tool results.
     *
     * A search result is several kilobytes of specs. Replayed verbatim on every
     * later turn, a four-turn conversation sends the same laptop specs four
     * times and the request outgrows a free tier's per-minute token cap — which
     * is a real 429, not a hypothetical. Recent results stay whole because the
     * agent is actively reasoning about them; older ones keep only what it
     * still needs, which is the id, the name and the price.
     */
    private List<LlmMessage> rebuildHistory(UUID conversationId) {
        List<LlmMessage> history = new ArrayList<>();
        List<ConversationMessage> all = messages.findByConversationIdOrderBySeqAsc(conversationId);

        long toolMessages = all.stream().filter(m -> m.getRole() == MessageRole.TOOL).count();
        long keepWholeAfter = toolMessages - props.getFullToolResultsInHistory();
        long seenTools = 0;

        for (ConversationMessage m : all) {
            switch (m.getRole()) {
                case USER -> history.add(LlmMessage.user(m.getContent()));
                case ASSISTANT -> history.add(LlmMessage.model(m.getContent()));
                case TOOL -> {
                    // One stored row reconstructs the pair the provider expects:
                    // the model's call turn, then the result turn — including any
                    // opaque signature, without which Gemini 3 rejects the request.
                    seenTools++;
                    String toolCallId = storedMeta(m, "toolCallId");
                    Map<String, Object> result = m.getToolResult() == null ? Map.of() : m.getToolResult();
                    if (seenTools <= keepWholeAfter) {
                        result = compact(result);
                    }
                    history.add(LlmMessage.modelToolCalls(List.of(
                            new LlmToolCall(toolCallId, m.getToolName(),
                                    m.getToolArgs() == null ? Map.of() : m.getToolArgs(),
                                    storedMeta(m, "signature")))));
                    history.add(LlmMessage.toolResult(toolCallId, m.getToolName(), result));
                }
            }
        }
        return history;
    }

    /**
     * Provider-opaque state that has to survive the history rebuild:
     * Gemini's thoughtSignature, Groq's tool_call_id. Both are blobs — stored,
     * returned, never interpreted.
     */
    private static Map<String, Object> toolMetaOf(LlmToolCall call) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (call.signature() != null && !call.signature().isBlank()) {
            meta.put("signature", call.signature());
        }
        if (call.id() != null && !call.id().isBlank()) {
            meta.put("toolCallId", call.id());
        }
        return meta.isEmpty() ? null : meta;
    }

    private static String storedMeta(ConversationMessage m, String key) {
        if (m.getToolMeta() == null) return null;
        Object value = m.getToolMeta().get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** Keys worth keeping when an older result is trimmed — enough to keep talking about it. */
    private static final List<String> KEEP = List.of(
            "id", "modelName", "price", "basePrice", "stockQty", "reason",
            "approvedPct", "offerId", "orderId", "finalPrice", "status", "identityKey");

    @SuppressWarnings("unchecked")
    private Map<String, Object> compact(Map<String, Object> result) {
        Object data = result.get("data");
        Map<String, Object> out = new LinkedHashMap<>(result);
        if (data instanceof List<?> list) {
            out.put("data", list.stream().map(this::compactItem).toList());
        } else if (data != null) {
            out.put("data", compactItem(data));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object compactItem(Object item) {
        Map<String, Object> asMap;
        if (item instanceof Map<?, ?> m) {
            asMap = (Map<String, Object>) m;
        } else {
            asMap = mapper.convertValue(item, Map.class);
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        asMap.forEach((k, v) -> {
            if (KEEP.contains(k)) {
                kept.put(k, v);
            } else if ("product".equals(k)) {
                kept.put(k, compactItem(v));
            }
        });
        return kept.isEmpty() ? asMap : kept;
    }

    private void persist(Conversation conv, int seq, MessageRole role, String content, String toolName,
                         Map<String, Object> args, Map<String, Object> result, Boolean ok,
                         Integer latencyMs, Map<String, Object> toolMeta) {
        ConversationMessage m = new ConversationMessage();
        m.setConversationId(conv.getId());
        m.setSeq(seq);
        m.setRole(role);
        m.setContent(truncate(content));
        m.setToolName(toolName);
        m.setToolArgs(args);
        m.setToolResult(result);
        m.setToolOk(ok);
        m.setLatencyMs(latencyMs);
        m.setToolMeta(toolMeta);
        messages.save(m);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 8000 ? s : s.substring(0, 8000);
    }

    private String errorCode(ToolOutcome outcome) {
        Object err = outcome.payload().get("error");
        if (err instanceof Map<?, ?> map) {
            Object code = map.get("code");
            return code == null ? "UNKNOWN" : String.valueOf(code);
        }
        return "UNKNOWN";
    }
}