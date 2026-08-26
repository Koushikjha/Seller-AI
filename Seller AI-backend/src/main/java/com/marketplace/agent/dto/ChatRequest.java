package com.marketplace.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatRequest(
        /* omit to start a new conversation */
        UUID conversationId,
        @NotBlank @Size(max = 4000) String message
) {}
