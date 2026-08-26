package com.marketplace.laptop.service;

import com.marketplace.common.NotFoundException;
import com.marketplace.laptop.dto.CompareResponse;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.laptop.spec.ExtraSpecKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds an aligned comparison table. Brand / sub-brand / cpu / gpu are joined
 * automatically so the agent never has to make follow-up calls to explain a
 * difference it is already showing.
 */
@Service
@Transactional(readOnly = true)
public class LaptopCompareService {

    private final LaptopRepository repo;

    public LaptopCompareService(LaptopRepository repo) {
        this.repo = repo;
    }

    public CompareResponse compare(List<UUID> ids) {
        List<Laptop> found = repo.findAllByIdWithJoins(ids);
        if (found.size() != ids.size()) {
            List<UUID> missing = ids.stream()
                    .filter(id -> found.stream().noneMatch(l -> l.getId().equals(id)))
                    .toList();
            throw new NotFoundException("Laptop", missing);
        }
        // preserve caller-supplied order
        List<Laptop> ordered = ids.stream()
                .map(id -> found.stream().filter(l -> l.getId().equals(id)).findFirst().orElseThrow())
                .toList();

        List<CompareResponse.Column> columns = ordered.stream()
                .map(l -> new CompareResponse.Column(l.getId(),
                        l.getSubBrand().getBrand().getName() + " " + l.getModelName()))
                .toList();

        List<CompareResponse.Row> rows = new ArrayList<>();
        addRow(rows, ordered, "brand", l -> l.getSubBrand().getBrand().getName());
        addRow(rows, ordered, "subBrand", l -> l.getSubBrand().getName());
        addRow(rows, ordered, "segment", l -> l.getSubBrand().getSegment());
        addRow(rows, ordered, "priceTier", l -> l.getSubBrand().getPriceTier());
        addRow(rows, ordered, "buildQuality", l -> l.getSubBrand().getBuildQualityTier());
        addRow(rows, ordered, "basePrice", Laptop::getBasePrice);
        addRow(rows, ordered, "maxDiscountPct", Laptop::getMaxDiscountPct);
        addRow(rows, ordered, "stockQty", Laptop::getStockQty);
        addRow(rows, ordered, "cpu", l -> l.getCpu().getName());
        addRow(rows, ordered, "cpuCores", l -> l.getCpu().getCores());
        addRow(rows, ordered, "cpuBenchmarkTier", l -> l.getCpu().getBenchmarkTier());
        addRow(rows, ordered, "gpu", l -> l.getGpu() == null ? "Integrated" : l.getGpu().getName());
        addRow(rows, ordered, "gpuVramGb", l -> l.getGpu() == null ? null : l.getGpu().getVramGb());
        addRow(rows, ordered, "gpuBenchmarkTier", l -> l.getGpu() == null ? null : l.getGpu().getBenchmarkTier());
        addRow(rows, ordered, "ramGb", Laptop::getRamGb);
        addRow(rows, ordered, "ramType", Laptop::getRamType);
        addRow(rows, ordered, "storageGb", Laptop::getStorageGb);
        addRow(rows, ordered, "storageType", Laptop::getStorageType);
        addRow(rows, ordered, "displayInches", Laptop::getDisplayInches);
        addRow(rows, ordered, "displayType", Laptop::getDisplayType);
        addRow(rows, ordered, "refreshRateHz", Laptop::getRefreshRateHz);
        addRow(rows, ordered, "touchscreen", Laptop::isTouchscreen);
        addRow(rows, ordered, "weightKg", Laptop::getWeightKg);
        addRow(rows, ordered, "batteryHours", Laptop::getBatteryHours);
        addRow(rows, ordered, "os", Laptop::getOs);
        addRow(rows, ordered, "releaseYear", Laptop::getReleaseYear);
        addRow(rows, ordered, "warrantyMonths", l -> l.getSubBrand().getBrand().getDefaultWarrantyMonths());

        for (ExtraSpecKey key : ExtraSpecKey.values()) {
            boolean anyPresent = ordered.stream()
                    .anyMatch(l -> l.getExtraSpecs() != null && l.getExtraSpecs().get(key.name()) != null);
            if (anyPresent) {
                addRow(rows, ordered, "extraSpecs." + key.name(),
                        l -> l.getExtraSpecs() == null ? null : l.getExtraSpecs().get(key.name()));
            }
        }

        rows.sort(Comparator.comparing(CompareResponse.Row::differing).reversed());
        return new CompareResponse(columns, rows);
    }

    private void addRow(List<CompareResponse.Row> rows, List<Laptop> laptops,
                        String attribute, Function<Laptop, Object> extractor) {
        List<Object> values = laptops.stream().map(extractor).map(v -> (Object) v).toList();
        boolean differing = values.stream().distinct().count() > 1;
        rows.add(new CompareResponse.Row(attribute, values, differing));
    }
}
