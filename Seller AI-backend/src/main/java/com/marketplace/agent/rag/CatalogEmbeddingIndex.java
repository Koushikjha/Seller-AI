package com.marketplace.agent.rag;

import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.repository.LaptopRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Semantic search over the catalog, entirely in-process.
 *
 * Deliberately not a hosted vector DB: the catalog is small (tens of
 * laptops, not millions), so an in-memory store rebuilt from MySQL on every
 * boot is simpler, free, and has nothing external to run or pay for — the
 * same "does this need to be bigger than the problem" instinct that shaped
 * GigCover's bounded RandomForest and this project's own token-conscious
 * history compaction (see SalesAgentService.rebuildHistory).
 *
 * Embedding model is all-MiniLM-L6-v2 running locally via ONNX — no API
 * key, no network call. That makes this orthogonal to which chat provider
 * (Gemini/Groq/Cerebras) is doing the reasoning: retrieval never depends on
 * which one is currently configured.
 *
 * Rebuilt fresh on every startup rather than persisted. For a catalog this
 * size that's a sub-second cost, and it means the index can never drift out
 * of sync with the database the way a persisted one could after a manual
 * catalog edit.
 */
@Component
public class CatalogEmbeddingIndex {

    private static final Logger log = LoggerFactory.getLogger(CatalogEmbeddingIndex.class);

    private final LaptopRepository laptops;
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

    public CatalogEmbeddingIndex(LaptopRepository laptops) {
        this.laptops = laptops;
    }

    /** Wait for the app (and its DB connection) to actually be up before indexing. */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        List<Laptop> all = laptops.findAll();
        int indexed = 0;
        for (Laptop l : all) {
            if (!l.isInStock()) continue;
            String text = describe(l);
            Embedding embedding = embeddingModel.embed(text).content();
            TextSegment segment = TextSegment.from(text, Metadata.from("laptopId", l.getId().toString()));
            store.add(embedding, segment);
            indexed++;
        }
        log.info("Catalog embedding index built: {} in-stock laptops indexed", indexed);
    }

    /**
     * Plain-language description built from real catalog fields — this is
     * what the customer's own phrasing gets matched against, so it reads
     * like a spec sheet in prose, not a template with blanks filled in.
     */
    private String describe(Laptop l) {
        StringBuilder sb = new StringBuilder();
        sb.append(l.getSubBrand().getBrand().getName()).append(' ')
                .append(l.getSubBrand().getName()).append(' ')
                .append(l.getModelName()).append(". ");
        sb.append(l.getSubBrand().getSegment()).append(", ")
                .append(l.getSubBrand().getPriceTier()).append(" tier. ");
        sb.append(l.getCpu().getName());
        sb.append(l.getGpu() != null ? " with " + l.getGpu().getName() : ", integrated graphics");
        sb.append(". ").append(l.getRamGb()).append("GB RAM, ")
                .append(l.getStorageGb()).append("GB ").append(l.getStorageType()).append(". ");
        if (l.getDisplayInches() != null) {
            sb.append(l.getDisplayInches()).append("\" ").append(l.getDisplayType());
            if (l.getRefreshRateHz() != null) sb.append(' ').append(l.getRefreshRateHz()).append("Hz");
            if (l.isTouchscreen()) sb.append(" touchscreen");
            sb.append(". ");
        }
        if (l.getWeightKg() != null) sb.append(l.getWeightKg()).append("kg. ");
        if (l.getBatteryHours() != null) sb.append(l.getBatteryHours()).append("h battery. ");
        if (l.getOs() != null) sb.append(l.getOs()).append(". ");
        return sb.toString();
    }

    /**
     * @param query free-text description of what the customer wants
     * @param maxResults top-k cutoff
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        // NOTE: findRelevant is the stable convenience method across recent
        // 1.x releases. If your installed LangChain4j version has removed
        // it in favor of store.search(EmbeddingSearchRequest...), swap this
        // one line — everything else here is unaffected.
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.0)
                .build();

        return store.search(request).matches();
    }
}