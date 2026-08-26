package com.marketplace.catalog.core;

/**
 * One allowed key in a catalog item's free-form {@code extraSpecs} map.
 *
 * The set of SpecKeys for a device type is simultaneously:
 *   1. the merchant admin UI's "add specification" dropdown,
 *   2. the whitelist enforced on create/update, and
 *   3. the ONLY extraSpecs vocabulary the agent may reference.
 *
 * Keeping all three off one enum is what stops a merchant from typing a spec
 * the agent has no words for -- and stops the agent inventing one.
 */
public interface SpecKey {
    String key();
    String type();          // "string" | "boolean" | "number" | "string[]"
    String description();    // nullable
}
