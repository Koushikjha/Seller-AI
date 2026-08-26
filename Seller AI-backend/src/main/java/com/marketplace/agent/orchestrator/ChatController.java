package com.marketplace.agent.orchestrator;

import com.marketplace.agent.dto.ChatRequest;
import com.marketplace.agent.dto.ChatResponse;
import com.marketplace.agent.dto.TranscriptEntry;
import com.marketplace.agent.llm.LlmClient;
import com.marketplace.agent.state.ConversationMessageRepository;
import com.marketplace.agent.state.ConversationRepository;
import com.marketplace.common.ApiResponse;
import com.marketplace.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
@Tag(name = "chat", description = "The sales agent. One endpoint the frontend talks to.")
public class ChatController {

    private final SalesAgentService agent;
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final LlmClient llm;

    public ChatController(SalesAgentService agent, ConversationRepository conversations,
                          ConversationMessageRepository messages, LlmClient llm) {
        this.agent = agent;
        this.conversations = conversations;
        this.messages = messages;
        this.llm = llm;
    }

    @PostMapping
    @Operation(summary = "Send a customer message. Omit conversationId to start a new conversation.")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(agent.chat(req.conversationId(), req.message()));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Full transcript including every tool call and result — the audit trail")
    public ApiResponse<List<TranscriptEntry>> transcript(@PathVariable UUID conversationId) {
        if (!conversations.existsById(conversationId)) {
            throw new NotFoundException("Conversation", conversationId);
        }
        return ApiResponse.ok(messages.findByConversationIdOrderBySeqAsc(conversationId)
                .stream().map(TranscriptEntry::from).toList());
    }

    @GetMapping("/{conversationId}/state")
    @Operation(summary = "Current sales state — the structured memory held outside the LLM")
    public ApiResponse<Map<String, Object>> state(@PathVariable UUID conversationId) {
        var conv = conversations.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationId));
        return ApiResponse.ok(new java.util.LinkedHashMap<>(Map.of(
                "conversationId", conv.getId(),
                "stage", conv.getStage().name(),
                "identityVerified", conv.identityKey() != null,
                "negotiationRounds", conv.getNegotiationRounds(),
                "questionsAsked", conv.getQuestionsAsked(),
                "toolCallsTotal", conv.getToolCallsTotal(),
                "candidateCount", conv.getCandidateIds() == null ? 0 : conv.getCandidateIds().size(),
                "objections", conv.getObjections() == null ? List.of() : conv.getObjections())));
    }

    @GetMapping("/meta/provider")
    @Operation(summary = "Which LLM client is wired in right now")
    public ApiResponse<Map<String, String>> provider() {
        return ApiResponse.ok(Map.of("provider", llm.name()));
    }
}
