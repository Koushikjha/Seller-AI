package com.marketplace.laptop.service;

import com.marketplace.catalog.core.*;
import com.marketplace.laptop.dto.LaptopSearchCriteria;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.laptop.spec.ExtraSpecKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Adapts the laptop module to the generic catalog contract. */
@Component
@Transactional(readOnly = true)
public class LaptopCatalogProvider implements CatalogProvider {

    private final LaptopRepository repo;
    private final LaptopSearchService search;

    public LaptopCatalogProvider(LaptopRepository repo, LaptopSearchService search) {
        this.repo = repo;
        this.search = search;
    }

    @Override public DeviceType deviceType() { return DeviceType.LAPTOP; }

    @Override public List<ExtraSpecKey> specVocabulary() { return ExtraSpecKey.vocabulary(); }

    @Override public List<String> deviceSpecificFilters() {
        return List.of("storageType", "os", "refreshRateMin", "maxWeightKg", "minBatteryHours",
                "touchscreen", "displayType", "gpuBrand", "gpuBenchmarkTier",
                "discreteGpuRequired", "minVramGb", "modelNameContains", "sort");
    }

    @Override
    public Optional<CatalogItemView> findById(UUID id) {
        return repo.findById(id).map(LaptopCatalogProvider::toView);
    }

    @Override
    public List<CatalogItemView> search(CatalogQuery q) {
        Map<String, String> extra = q.deviceSpecific() == null ? Map.of() : q.deviceSpecific();
        LaptopSearchCriteria criteria = new LaptopSearchCriteria(
                q.minPrice(), q.maxPrice(), q.minRamGb(), q.minStorageGb(),
                extra.get("storageType"),
                extra.get("os"),
                intOrNull(extra.get("refreshRateMin")),
                decOrNull(extra.get("maxWeightKg")),
                intOrNull(extra.get("minBatteryHours")),
                boolOrNull(extra.get("touchscreen")),
                extra.get("displayType"),
                q.brand(), extra.get("subBrand"), q.segment(), q.priceTier(), q.cpuBenchmarkTier(),
                extra.get("gpuBrand"), extra.get("gpuBenchmarkTier"),
                boolOrNull(extra.get("discreteGpuRequired")),
                intOrNull(extra.get("minVramGb")),
                extra.get("modelNameContains"),
                q.inStockOnly(), q.effectiveLimit(), extra.get("sort"));

        return search.searchEntities(criteria).stream().map(LaptopCatalogProvider::toView).toList();
    }

    static CatalogItemView toView(Laptop l) {
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("cpu", l.getCpu().getName());
        specs.put("cpuBenchmarkTier", l.getCpu().getBenchmarkTier());
        specs.put("gpu", l.getGpu() == null ? "Integrated" : l.getGpu().getName());
        specs.put("gpuVramGb", l.getGpu() == null ? null : l.getGpu().getVramGb());
        specs.put("ramGb", l.getRamGb());
        specs.put("ramType", l.getRamType());
        specs.put("storageGb", l.getStorageGb());
        specs.put("storageType", l.getStorageType());
        specs.put("displayInches", l.getDisplayInches());
        specs.put("displayType", l.getDisplayType());
        specs.put("refreshRateHz", l.getRefreshRateHz());
        specs.put("touchscreen", l.isTouchscreen());
        specs.put("weightKg", l.getWeightKg());
        specs.put("batteryHours", l.getBatteryHours());
        specs.put("os", l.getOs());
        specs.put("releaseYear", l.getReleaseYear());
        specs.values().removeIf(Objects::isNull);

        return new CatalogItemView(
                l.getId(), DeviceType.LAPTOP,
                l.getSubBrand().getBrand().getName(),
                l.getSubBrand().getName(),
                l.getSubBrand().getSegment(),
                l.getSubBrand().getPriceTier(),
                l.getModelName(), l.getBasePrice(), l.getMaxDiscountPct(),
                l.getStockQty(), l.isInStock(), specs,
                l.getExtraSpecs() == null ? Map.of() : l.getExtraSpecs());
    }

    private static Integer intOrNull(String s) {
        return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
    }

    private static java.math.BigDecimal decOrNull(String s) {
        return s == null || s.isBlank() ? null : new java.math.BigDecimal(s.trim());
    }

    private static Boolean boolOrNull(String s) {
        return s == null || s.isBlank() ? null : Boolean.valueOf(s.trim());
    }
}
