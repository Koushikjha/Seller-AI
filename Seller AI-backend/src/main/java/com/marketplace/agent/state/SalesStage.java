package com.marketplace.agent.state;

/**
 * Where the conversation currently is. Deliberately NOT a rigid state
 * machine — the agent moves between these freely, including backwards
 * (PRODUCT_PRESENTATION -> OBJECTION_HANDLING -> PRODUCT_SEARCH is a
 * completely normal path). The stage is a description of the situation,
 * not a gate on what the agent may do next.
 */
public enum SalesStage {
    DISCOVERY,
    QUALIFICATION,
    PRODUCT_SEARCH,
    PRODUCT_PRESENTATION,
    OBJECTION_HANDLING,
    NEGOTIATION,
    CLOSING,
    CHECKOUT,
    ENDED
}
