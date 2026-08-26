package com.marketplace.agent.dto;

import com.marketplace.agent.state.ConversationMessage;
import com.marketplace.agent.state.MessageRole;

import java.time.Instant;
import java.util.Map;

public record TranscriptEntry(
        int seq,
        MessageRole role,
        String content,
        String toolName,
        Map<String, Object> toolArgs,
        Map<String, Object> toolResult,
        Boolean toolOk,
        Instant at
) {
    public static TranscriptEntry from(ConversationMessage m) {
        return new TranscriptEntry(m.getSeq(), m.getRole(), m.getContent(), m.getToolName(),
                m.getToolArgs(), m.getToolResult(), m.getToolOk(), m.getCreatedAt());
    }
}
