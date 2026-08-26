package com.marketplace.catalog.core;

/**
 * Device types the store can carry. Adding one = add the enum constant and
 * register a {@link CatalogProvider} for it. Nothing else in the agent-facing
 * layer changes.
 */
public enum DeviceType {
    LAPTOP,
    SMARTPHONE
}
