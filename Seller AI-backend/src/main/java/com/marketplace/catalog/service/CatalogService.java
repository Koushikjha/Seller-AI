package com.marketplace.catalog.service;

import com.marketplace.catalog.dto.*;
import com.marketplace.catalog.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final BrandRepository brands;
    private final SubBrandRepository subBrands;
    private final CpuRepository cpus;
    private final GpuRepository gpus;

    public CatalogService(BrandRepository brands, SubBrandRepository subBrands,
                          CpuRepository cpus, GpuRepository gpus) {
        this.brands = brands;
        this.subBrands = subBrands;
        this.cpus = cpus;
        this.gpus = gpus;
    }

    public List<BrandDto> listBrands() {
        return brands.findAll().stream()
                .sorted(Comparator.comparing(b -> b.getName().toLowerCase()))
                .map(BrandDto::from).toList();
    }

    public List<SubBrandDto> listSubBrands(UUID brandId) {
        var source = brandId == null ? subBrands.findAll() : subBrands.findByBrandId(brandId);
        return source.stream()
                .sorted(Comparator.comparing(sb -> sb.getName().toLowerCase()))
                .map(SubBrandDto::from).toList();
    }

    public List<CpuDto> listCpus() {
        return cpus.findAll().stream().map(CpuDto::from).toList();
    }

    public List<GpuDto> listGpus() {
        return gpus.findAll().stream().map(GpuDto::from).toList();
    }
}
