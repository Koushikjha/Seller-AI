package com.marketplace.catalog.core;

import com.marketplace.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalogRegistry {

    private final Map<DeviceType, CatalogProvider> providers = new EnumMap<>(DeviceType.class);

    public CatalogRegistry(List<CatalogProvider> discovered) {
        discovered.forEach(p -> providers.put(p.deviceType(), p));
    }

    public CatalogProvider get(DeviceType type) {
        CatalogProvider p = providers.get(type);
        if (p == null) {
            throw new NotFoundException("No catalog provider for device type", type);
        }
        return p;
    }

    public List<CatalogProvider> all() {
        return List.copyOf(providers.values());
    }

    public List<DeviceType> supportedTypes() {
        return List.copyOf(providers.keySet());
    }
}
