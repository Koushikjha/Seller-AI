package com.marketplace.laptop.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CompareRequest(
        @NotEmpty @Size(min = 2, max = 5, message = "compare between 2 and 5 laptops")
        List<UUID> ids
) {}
