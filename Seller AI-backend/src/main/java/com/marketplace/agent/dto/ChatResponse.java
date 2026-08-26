package com.marketplace.agent.dto;

import com.marketplace.agent.state.SalesStage;
import com.marketplace.laptop.dto.LaptopSummaryDto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        String reply,
        SalesStage stage,
        boolean identityVerified,
        List<ToolCallView> toolCalls,
        List<LaptopSummaryDto> candidates,
        UUID selectedLaptopId,
        UUID discountOfferId,
        UUID orderId
) {}