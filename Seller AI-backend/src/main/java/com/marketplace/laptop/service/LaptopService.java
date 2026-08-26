package com.marketplace.laptop.service;

import com.marketplace.catalog.core.SpecKeyValidator;
import com.marketplace.catalog.entity.Cpu;
import com.marketplace.catalog.entity.Gpu;
import com.marketplace.catalog.entity.SubBrand;
import com.marketplace.catalog.repository.CpuRepository;
import com.marketplace.catalog.repository.GpuRepository;
import com.marketplace.catalog.repository.SubBrandRepository;
import com.marketplace.common.NotFoundException;
import com.marketplace.laptop.dto.LaptopDto;
import com.marketplace.laptop.dto.LaptopUpsertRequest;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.laptop.spec.ExtraSpecKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class LaptopService {

    private final LaptopRepository laptops;
    private final SubBrandRepository subBrands;
    private final CpuRepository cpus;
    private final GpuRepository gpus;

    public LaptopService(LaptopRepository laptops, SubBrandRepository subBrands,
                         CpuRepository cpus, GpuRepository gpus) {
        this.laptops = laptops;
        this.subBrands = subBrands;
        this.cpus = cpus;
        this.gpus = gpus;
    }

    @Transactional(readOnly = true)
    public Laptop require(UUID id) {
        return laptops.findById(id).orElseThrow(() -> new NotFoundException("Laptop", id));
    }

    @Transactional(readOnly = true)
    public LaptopDto get(UUID id) {
        return LaptopDto.from(require(id));
    }

    @Transactional(readOnly = true)
    public List<LaptopDto> listAll() {
        return laptops.findAll().stream().map(LaptopDto::from).toList();
    }

    public LaptopDto create(LaptopUpsertRequest req) {
        Laptop l = new Laptop();
        apply(l, req);
        return LaptopDto.from(laptops.save(l));
    }

    public LaptopDto update(UUID id, LaptopUpsertRequest req) {
        Laptop l = require(id);
        apply(l, req);
        return LaptopDto.from(laptops.save(l));
    }

    public LaptopDto updateStock(UUID id, int stockQty) {
        Laptop l = require(id);
        l.setStockQty(stockQty);
        return LaptopDto.from(laptops.save(l));
    }

    public void delete(UUID id) {
        Laptop l = require(id);
        laptops.delete(l);
    }

    /**
     * Merchant input lands here and nowhere else. extraSpecs is whitelisted
     * against ExtraSpecKey so a merchant cannot introduce a spec the agent has
     * no vocabulary for.
     */
    private void apply(Laptop l, LaptopUpsertRequest req) {
        SpecKeyValidator.validate(req.extraSpecs(), ExtraSpecKey.vocabulary());

        SubBrand subBrand = subBrands.findById(req.subBrandId())
                .orElseThrow(() -> new NotFoundException("SubBrand", req.subBrandId()));
        Cpu cpu = cpus.findById(req.cpuId())
                .orElseThrow(() -> new NotFoundException("Cpu", req.cpuId()));
        Gpu gpu = req.gpuId() == null ? null : gpus.findById(req.gpuId())
                .orElseThrow(() -> new NotFoundException("Gpu", req.gpuId()));

        l.setSubBrand(subBrand);
        l.setCpu(cpu);
        l.setGpu(gpu);
        l.setModelName(req.modelName());
        l.setBasePrice(req.basePrice());
        l.setMaxDiscountPct(req.maxDiscountPct() == null ? BigDecimal.ZERO : req.maxDiscountPct());
        l.setStockQty(req.stockQty());
        l.setRamGb(req.ramGb());
        l.setRamType(req.ramType());
        l.setStorageGb(req.storageGb());
        l.setStorageType(req.storageType());
        l.setDisplayInches(req.displayInches());
        l.setDisplayType(req.displayType());
        l.setRefreshRateHz(req.refreshRateHz());
        l.setTouchscreen(Boolean.TRUE.equals(req.touchscreen()));
        l.setWeightKg(req.weightKg());
        l.setBatteryHours(req.batteryHours());
        l.setOs(req.os());
        l.setReleaseYear(req.releaseYear());
        l.setExtraSpecs(req.extraSpecs() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(req.extraSpecs()));
    }
}
