package com.marketplace.catalog.dto;

import com.marketplace.catalog.entity.Cpu;

import java.math.BigDecimal;
import java.util.UUID;

public record CpuDto(
        UUID id, String name, String manufacturer, Integer cores, Integer threads,
        BigDecimal baseClockGhz, BigDecimal boostClockGhz, Integer tdpWatts, String benchmarkTier
) {
    public static CpuDto from(Cpu c) {
        return new CpuDto(c.getId(), c.getName(), c.getManufacturer(), c.getCores(), c.getThreads(),
                c.getBaseClockGhz(), c.getBoostClockGhz(), c.getTdpWatts(), c.getBenchmarkTier());
    }
}
