package com.marketplace.laptop.dto;

import java.util.List;

/** Replace a product's photo list. An empty list clears back to the placeholder. */
public record ImagesUpdateRequest(List<String> images) {}