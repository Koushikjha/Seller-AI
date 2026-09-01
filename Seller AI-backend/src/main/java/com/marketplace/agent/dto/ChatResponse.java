package com.marketplace.agent.dto;

import com.marketplace.agent.state.SalesStage;
import com.marketplace.laptop.dto.PresentedProductDto;

import java.util.List;
import java.util.UUID;

/**
 * What the widget renders. {@code products} carries a reason per item when the
 * agent presented them deliberately via present_products, and a null reason
 * when they merely came back from a search.
 */
public record ChatResponse(
        UUID conversationId,
        String reply,
        SalesStage stage,
        boolean identityVerified,
        List<ToolCallView> toolCalls,
        List<PresentedProductDto> products,
        UUID selectedLaptopId,
        UUID discountOfferId,
        UUID orderId
) {}