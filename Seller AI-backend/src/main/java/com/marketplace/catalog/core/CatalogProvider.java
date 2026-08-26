package com.marketplace.catalog.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The extension point. One implementation per device type.
 * The agent-facing catalog endpoints and the tool manifest are written
 * against this interface only -- they never import a device package.
 */
public interface CatalogProvider {

    DeviceType deviceType();

    /** Whitelisted extraSpecs vocabulary for this device type. */
    List<? extends SpecKey> specVocabulary();

    Optional<CatalogItemView> findById(UUID id);

    List<CatalogItemView> search(CatalogQuery query);

    /** Filter names this device type understands beyond the shared CatalogQuery fields. */
    default List<String> deviceSpecificFilters() {
        return List.of();
    }
}
