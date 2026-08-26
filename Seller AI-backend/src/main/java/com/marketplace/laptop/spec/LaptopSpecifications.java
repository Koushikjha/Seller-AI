package com.marketplace.laptop.spec;

import com.marketplace.laptop.dto.LaptopSearchCriteria;
import com.marketplace.laptop.entity.Laptop;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * All search filtering lives here as JPA Specifications so the join predicates
 * across cpu / gpu / sub_brand stay in one readable place.
 *
 * Note the in-stock predicate: it is applied unless the caller explicitly asks
 * for out-of-stock rows. The agent's search tool never sets that flag, so the
 * agent physically cannot be handed something the shop can't sell.
 */
public final class LaptopSpecifications {

    private LaptopSpecifications() {}

    public static Specification<Laptop> from(LaptopSearchCriteria c) {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                var subBrand = root.fetch("subBrand", JoinType.INNER);
                subBrand.fetch("brand", JoinType.INNER);
                root.fetch("cpu", JoinType.INNER);
                root.fetch("gpu", JoinType.LEFT);
                query.distinct(true);
            }

            var subBrandJoin = root.join("subBrand", JoinType.INNER);
            var brandJoin = subBrandJoin.join("brand", JoinType.INNER);
            var cpuJoin = root.join("cpu", JoinType.INNER);
            var gpuJoin = root.join("gpu", JoinType.LEFT);

            List<Predicate> p = new ArrayList<>();

            if (c.inStockOnly()) {
                p.add(cb.greaterThan(root.get("stockQty"), 0));
            }
            if (c.minPrice() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("basePrice"), c.minPrice()));
            }
            if (c.maxPrice() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("basePrice"), c.maxPrice()));
            }
            if (c.minRam() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("ramGb"), c.minRam()));
            }
            if (c.minStorage() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("storageGb"), c.minStorage()));
            }
            if (c.storageType() != null) {
                p.add(cb.equal(cb.upper(root.get("storageType")), c.storageType().toUpperCase()));
            }
            if (c.os() != null) {
                p.add(cb.like(cb.lower(root.get("os")), "%" + c.os().toLowerCase() + "%"));
            }
            if (c.refreshRateMin() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("refreshRateHz"), c.refreshRateMin()));
            }
            if (c.maxWeightKg() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("weightKg"), c.maxWeightKg()));
            }
            if (c.minBatteryHours() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("batteryHours"), c.minBatteryHours()));
            }
            if (c.touchscreen() != null) {
                p.add(cb.equal(root.get("touchscreen"), c.touchscreen()));
            }
            if (c.displayType() != null) {
                p.add(cb.equal(cb.upper(root.get("displayType")), c.displayType().toUpperCase()));
            }
            if (c.brand() != null) {
                p.add(cb.equal(cb.lower(brandJoin.get("name")), c.brand().toLowerCase()));
            }
            if (c.subBrand() != null) {
                p.add(cb.equal(cb.lower(subBrandJoin.get("name")), c.subBrand().toLowerCase()));
            }
            if (c.segment() != null) {
                p.add(cb.equal(cb.upper(subBrandJoin.get("segment")), c.segment().toUpperCase()));
            }
            if (c.priceTier() != null) {
                p.add(cb.equal(cb.upper(subBrandJoin.get("priceTier")), c.priceTier().toUpperCase()));
            }
            if (c.cpuBenchmarkTier() != null) {
                p.add(cb.equal(cb.upper(cpuJoin.get("benchmarkTier")), c.cpuBenchmarkTier().toUpperCase()));
            }
            if (c.gpuBrand() != null) {
                p.add(cb.equal(cb.lower(gpuJoin.get("manufacturer")), c.gpuBrand().toLowerCase()));
            }
            if (c.gpuBenchmarkTier() != null) {
                p.add(cb.equal(cb.upper(gpuJoin.get("benchmarkTier")), c.gpuBenchmarkTier().toUpperCase()));
            }
            if (Boolean.TRUE.equals(c.discreteGpuRequired())) {
                p.add(cb.and(cb.isNotNull(root.get("gpu")), cb.isFalse(gpuJoin.get("integrated"))));
            }
            if (c.minVramGb() != null) {
                p.add(cb.greaterThanOrEqualTo(gpuJoin.get("vramGb"), c.minVramGb()));
            }
            if (c.modelNameContains() != null) {
                p.add(cb.like(cb.lower(root.get("modelName")), "%" + c.modelNameContains().toLowerCase() + "%"));
            }

            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
