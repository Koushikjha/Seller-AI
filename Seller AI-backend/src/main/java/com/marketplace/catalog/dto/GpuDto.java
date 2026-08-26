package com.marketplace.catalog.dto;

import com.marketplace.catalog.entity.Gpu;

import java.util.UUID;

public record GpuDto(
        UUID id, String name, String manufacturer, Integer vramGb, String benchmarkTier, boolean integrated
) {
    public static GpuDto from(Gpu g) {
        return new GpuDto(g.getId(), g.getName(), g.getManufacturer(), g.getVramGb(),
                g.getBenchmarkTier(), g.isIntegrated());
    }
}
