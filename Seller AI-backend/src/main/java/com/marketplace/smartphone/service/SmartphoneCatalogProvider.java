package com.marketplace.smartphone.service;

import com.marketplace.catalog.core.*;
import com.marketplace.smartphone.entity.Smartphone;
import com.marketplace.smartphone.repository.SmartphoneRepository;
import com.marketplace.smartphone.spec.SmartphoneSpecKey;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Transactional(readOnly = true)
public class SmartphoneCatalogProvider implements CatalogProvider {

    private final SmartphoneRepository repo;

    public SmartphoneCatalogProvider(SmartphoneRepository repo) {
        this.repo = repo;
    }

    @Override public DeviceType deviceType() { return DeviceType.SMARTPHONE; }

    @Override public List<SmartphoneSpecKey> specVocabulary() { return SmartphoneSpecKey.vocabulary(); }

    @Override public List<String> deviceSpecificFilters() {
        return List.of("minBatteryMah", "minMainCameraMp", "os", "refreshRateMin");
    }

    @Override
    public Optional<CatalogItemView> findById(UUID id) {
        return repo.findById(id).map(SmartphoneCatalogProvider::toView);
    }

    @Override
    public List<CatalogItemView> search(CatalogQuery q) {
        Map<String, String> extra = q.deviceSpecific() == null ? Map.of() : q.deviceSpecific();

        Specification<Smartphone> spec = (root, query, cb) -> {
            var subBrand = root.join("subBrand", JoinType.INNER);
            var brand = subBrand.join("brand", JoinType.INNER);
            var cpu = root.join("cpu", JoinType.INNER);
            List<Predicate> p = new ArrayList<>();

            if (q.inStockOnly()) p.add(cb.greaterThan(root.get("stockQty"), 0));
            if (q.minPrice() != null) p.add(cb.greaterThanOrEqualTo(root.get("basePrice"), q.minPrice()));
            if (q.maxPrice() != null) p.add(cb.lessThanOrEqualTo(root.get("basePrice"), q.maxPrice()));
            if (q.minRamGb() != null) p.add(cb.greaterThanOrEqualTo(root.get("ramGb"), q.minRamGb()));
            if (q.minStorageGb() != null) p.add(cb.greaterThanOrEqualTo(root.get("storageGb"), q.minStorageGb()));
            if (q.brand() != null) p.add(cb.equal(cb.lower(brand.get("name")), q.brand().toLowerCase()));
            if (q.segment() != null) p.add(cb.equal(cb.upper(subBrand.get("segment")), q.segment().toUpperCase()));
            if (q.priceTier() != null) p.add(cb.equal(cb.upper(subBrand.get("priceTier")), q.priceTier().toUpperCase()));
            if (q.cpuBenchmarkTier() != null) p.add(cb.equal(cb.upper(cpu.get("benchmarkTier")), q.cpuBenchmarkTier().toUpperCase()));

            String minBattery = extra.get("minBatteryMah");
            if (minBattery != null && !minBattery.isBlank()) {
                p.add(cb.greaterThanOrEqualTo(root.get("batteryMah"), Integer.valueOf(minBattery.trim())));
            }
            String minCam = extra.get("minMainCameraMp");
            if (minCam != null && !minCam.isBlank()) {
                p.add(cb.greaterThanOrEqualTo(root.get("mainCameraMp"), Integer.valueOf(minCam.trim())));
            }
            String refresh = extra.get("refreshRateMin");
            if (refresh != null && !refresh.isBlank()) {
                p.add(cb.greaterThanOrEqualTo(root.get("refreshRateHz"), Integer.valueOf(refresh.trim())));
            }
            String os = extra.get("os");
            if (os != null && !os.isBlank()) {
                p.add(cb.like(cb.lower(root.get("os")), "%" + os.toLowerCase() + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };

        var page = PageRequest.of(0, q.effectiveLimit(), Sort.by(Sort.Direction.ASC, "basePrice"));
        return repo.findAll(spec, page).getContent().stream()
                .map(SmartphoneCatalogProvider::toView).toList();
    }

    static CatalogItemView toView(Smartphone s) {
        Map<String, Object> specs = new LinkedHashMap<>();
        specs.put("chipset", s.getCpu().getName());
        specs.put("chipsetBenchmarkTier", s.getCpu().getBenchmarkTier());
        specs.put("ramGb", s.getRamGb());
        specs.put("storageGb", s.getStorageGb());
        specs.put("displayInches", s.getDisplayInches());
        specs.put("refreshRateHz", s.getRefreshRateHz());
        specs.put("batteryMah", s.getBatteryMah());
        specs.put("mainCameraMp", s.getMainCameraMp());
        specs.put("os", s.getOs());
        specs.put("releaseYear", s.getReleaseYear());
        specs.values().removeIf(Objects::isNull);

        return new CatalogItemView(
                s.getId(), DeviceType.SMARTPHONE,
                s.getSubBrand().getBrand().getName(),
                s.getSubBrand().getName(),
                s.getSubBrand().getSegment(),
                s.getSubBrand().getPriceTier(),
                s.getModelName(), s.getBasePrice(), s.getMaxDiscountPct(),
                s.getStockQty(), s.isInStock(), specs,
                s.getExtraSpecs() == null ? Map.of() : s.getExtraSpecs());
    }
}
