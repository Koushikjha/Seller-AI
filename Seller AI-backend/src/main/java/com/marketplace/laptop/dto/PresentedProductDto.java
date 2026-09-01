package com.marketplace.laptop.dto;

/**
 * A product the agent has chosen to put in front of the customer, with its
 * own stated reason for choosing it.
 *
 * The reason is the agent's words. The product is the backend's record. That
 * separation is the whole point: the sales judgement is the model's, every
 * fact under it is verified.
 */
public record PresentedProductDto(LaptopSummaryDto product, String reason) {}