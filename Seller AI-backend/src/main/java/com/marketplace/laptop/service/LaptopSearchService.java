package com.marketplace.laptop.service;

import com.marketplace.laptop.dto.LaptopDto;
import com.marketplace.laptop.dto.LaptopSummaryDto;
import com.marketplace.laptop.dto.LaptopSearchCriteria;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.laptop.spec.LaptopSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LaptopSearchService {

    private final LaptopRepository repo;

    public LaptopSearchService(LaptopRepository repo) {
        this.repo = repo;
    }

    public List<Laptop> searchEntities(LaptopSearchCriteria criteria) {
        var pageable = PageRequest.of(0, criteria.effectiveLimit(), sortOf(criteria.sort()));
        return repo.findAll(LaptopSpecifications.from(criteria), pageable).getContent();
    }

    public List<LaptopDto> search(LaptopSearchCriteria criteria) {
        return searchEntities(criteria).stream().map(LaptopDto::from).toList();
    }

    /**
     * Compact results for the agent and for product cards. See LaptopSummaryDto
     * for why the full shape is the wrong thing to put in a model's context.
     */
    public List<LaptopSummaryDto> searchSummaries(LaptopSearchCriteria criteria) {
        return searchEntities(criteria).stream().map(LaptopSummaryDto::from).toList();
    }

    private Sort sortOf(String sort) {
        if (sort == null) return Sort.by(Sort.Direction.ASC, "basePrice");
        return switch (sort.toUpperCase()) {
            case "PRICE_DESC" -> Sort.by(Sort.Direction.DESC, "basePrice");
            case "RAM_DESC"   -> Sort.by(Sort.Direction.DESC, "ramGb");
            case "NEWEST"     -> Sort.by(Sort.Direction.DESC, "releaseYear");
            case "LIGHTEST"   -> Sort.by(Sort.Direction.ASC, "weightKg");
            default            -> Sort.by(Sort.Direction.ASC, "basePrice");
        };
    }
}