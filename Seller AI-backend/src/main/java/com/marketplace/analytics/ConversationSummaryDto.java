package com.marketplace.analytics;

import com.marketplace.agent.state.Conversation;
import com.marketplace.agent.state.SalesStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row in the merchant's conversation list. */
public record ConversationSummaryDto(
        UUID conversationId,
        SalesStage stage,
        String identityKey,
        boolean identityVerified,
        int negotiationRounds,
        int questionsAsked,
        int toolCallsTotal,
        int objectionCount,
        String selectedLaptop,
        BigDecimal approvedDiscountPct,
        UUID orderId,
        String orderStatus,
        BigDecimal orderValue,
        Instant startedAt,
        Instant lastActivityAt
) {
    public static ConversationSummaryDto from(Conversation c) {
        return new ConversationSummaryDto(
                c.getId(),
                c.getStage(),
                c.identityKey(),
                c.identityKey() != null,
                c.getNegotiationRounds(),
                c.getQuestionsAsked(),
                c.getToolCallsTotal(),
                c.getObjections() == null ? 0 : c.getObjections().size(),
                c.getSelectedLaptop() == null ? null : c.getSelectedLaptop().getModelName(),
                c.getDiscountOffer() == null ? null : c.getDiscountOffer().getApprovedPct(),
                c.getOrder() == null ? null : c.getOrder().getId(),
                c.getOrder() == null ? null : c.getOrder().getStatus().name(),
                c.getOrder() == null ? null : c.getOrder().getFinalPrice(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}