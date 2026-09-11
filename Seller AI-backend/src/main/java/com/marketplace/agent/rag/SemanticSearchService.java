package com.marketplace.agent.rag;

import com.marketplace.laptop.dto.LaptopSummaryDto;
import com.marketplace.laptop.repository.LaptopRepository;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Turns a fuzzy, descriptive query ("something for outdoor fieldwork, long
 * battery life") into ranked catalog matches — the complement to
 * search_laptops's exact structured filters, for when the customer isn't
 * thinking in spec fields.
 *
 * Deliberately returns the same {@link LaptopSummaryDto} shape search_laptops
 * does, so the agent (and present_products downstream of it) doesn't need to
 * handle two different result shapes depending on which search path fired.
 */
@Service
public class SemanticSearchService {

    private final CatalogEmbeddingIndex index;
    private final LaptopRepository laptops;

    public SemanticSearchService(CatalogEmbeddingIndex index, LaptopRepository laptops) {
        this.index = index;
        this.laptops = laptops;
    }

    public record Match(LaptopSummaryDto laptop, double score) {}

    public List<Match> search(String query, int k) {
        List<EmbeddingMatch<TextSegment>> hits = index.search(query, k);
        List<Match> out = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> hit : hits) {
            String idStr = hit.embedded().metadata().getString("laptopId");
            if (idStr == null) continue;
            // Re-fetched from the DB rather than trusting the embedded text —
            // same principle as ToolExecutor re-verifying ids elsewhere:
            // stock/price could have moved since the index was built.
            laptops.findById(UUID.fromString(idStr))
                    .ifPresent(l -> out.add(new Match(LaptopSummaryDto.from(l), hit.score())));
        }
        return out;
    }
}