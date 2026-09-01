package com.marketplace.laptop.service;

import com.marketplace.common.BusinessRuleException;
import com.marketplace.laptop.dto.LaptopSummaryDto;
import com.marketplace.laptop.dto.PresentedProductDto;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns "the agent wants to show these" into verified product cards.
 *
 * Presenting is an explicit, checked action rather than something inferred
 * from whatever search happened to return. Every id is re-read from the
 * database and re-checked for stock at the moment of presentation, so a model
 * that hallucinates an id, or reaches back to something that sold out three
 * turns ago, gets a structured refusal instead of a customer seeing a product
 * that does not exist.
 */
@Service
@Transactional(readOnly = true)
public class PresentationService {

    /** More than this in one breath is product dumping, not selling. */
    private static final int MAX_PRESENTED = 4;
    private static final int MAX_REASON_CHARS = 240;

    private final LaptopRepository laptops;

    public PresentationService(LaptopRepository laptops) {
        this.laptops = laptops;
    }

    public record Request(UUID laptopId, String reason) {}

    public List<PresentedProductDto> present(List<Request> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_PRESENT",
                    "present_products needs at least one item", null);
        }
        if (requests.size() > MAX_PRESENTED) {
            throw new BusinessRuleException("TOO_MANY_PRESENTED",
                    "Present at most " + MAX_PRESENTED + " products at once — "
                            + "pick the ones that actually fit and say why",
                    Map.of("limit", MAX_PRESENTED, "requested", requests.size()));
        }

        List<UUID> ids = requests.stream().map(Request::laptopId).toList();
        Map<UUID, Laptop> found = new LinkedHashMap<>();
        laptops.findAllByIdWithJoins(ids).forEach(l -> found.put(l.getId(), l));

        List<UUID> unknown = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("UNKNOWN_PRODUCT",
                    "These ids are not in the catalog. Use ids returned by a search — never invent one.",
                    Map.of("unknownIds", unknown));
        }

        List<UUID> outOfStock = found.values().stream()
                .filter(l -> l.getStockQty() <= 0)
                .map(Laptop::getId).toList();
        if (!outOfStock.isEmpty()) {
            throw new BusinessRuleException("OUT_OF_STOCK",
                    "One or more of those went out of stock. Search again and present what is sellable.",
                    Map.of("outOfStockIds", outOfStock));
        }

        List<PresentedProductDto> presented = new ArrayList<>();
        for (Request r : requests) {
            presented.add(new PresentedProductDto(
                    LaptopSummaryDto.from(found.get(r.laptopId())),
                    trim(r.reason())));
        }
        return presented;
    }

    private String trim(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String clean = reason.trim();
        return clean.length() <= MAX_REASON_CHARS ? clean : clean.substring(0, MAX_REASON_CHARS);
    }
}