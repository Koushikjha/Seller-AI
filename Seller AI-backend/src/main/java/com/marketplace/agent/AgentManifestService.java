package com.marketplace.agent;

import com.marketplace.agent.state.Conversation;
import com.marketplace.catalog.core.CatalogRegistry;
import com.marketplace.catalog.repository.BrandRepository;
import com.marketplace.catalog.repository.SubBrandRepository;
import com.marketplace.config.MarketplaceProperties;
import com.marketplace.webinfo.service.WebInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.marketplace.agent.ToolSchemas.*;

/**
 * Serves the agent's tool definitions and the live vocabulary behind them.
 *
 * Why this exists: filter values like segment and benchmark tier are merchant
 * data, so hard-coding them into a system prompt guarantees the prompt drifts
 * out of sync with the shop. Here the enums are read from the database at
 * request time, which means retuning the agent is a fetch, not an edit.
 */
@Service
@Transactional(readOnly = true)
public class AgentManifestService {

    private final CatalogRegistry registry;
    private final BrandRepository brands;
    private final SubBrandRepository subBrands;
    private final MarketplaceProperties props;

    public AgentManifestService(CatalogRegistry registry, BrandRepository brands,
                                SubBrandRepository subBrands, MarketplaceProperties props) {
        this.registry = registry;
        this.brands = brands;
        this.subBrands = subBrands;
        this.props = props;
    }



    // ------------------------------------------------------------------
    // live vocabulary
    // ------------------------------------------------------------------

    public Map<String, Object> vocabulary() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("deviceTypes", registry.supportedTypes().stream().map(Enum::name).toList());
        v.put("brands", brands.findAll().stream().map(b -> b.getName()).sorted().toList());
        v.put("subBrands", subBrands.findAll().stream()
                .map(sb -> sb.getBrand().getName() + " " + sb.getName()).sorted().toList());
        v.put("segments", distinct(subBrands.findAll().stream().map(sb -> sb.getSegment())));
        v.put("priceTiers", distinct(subBrands.findAll().stream().map(sb -> sb.getPriceTier())));
        v.put("benchmarkTiers", List.of("ENTRY", "MID", "HIGH", "FLAGSHIP"));
        v.put("storageTypes", List.of("SSD", "HDD"));
        v.put("displayTypes", List.of("FHD", "QHD", "4K", "OLED"));
        v.put("sortOptions", List.of("PRICE_ASC", "PRICE_DESC", "RAM_DESC", "NEWEST", "LIGHTEST"));
        v.put("extraSpecKeysByDeviceType", registry.all().stream().collect(Collectors.toMap(
                p -> p.deviceType().name(),
                p -> p.specVocabulary().stream().map(k -> Map.of(
                        "key", k.key(), "type", k.type(),
                        "description", k.description() == null ? "" : k.description())).toList(),
                (a, b) -> a, LinkedHashMap::new)));
        return v;
    }

    private List<String> distinct(java.util.stream.Stream<String> s) {
        return s.filter(Objects::nonNull).distinct().sorted().toList();
    }

    // ------------------------------------------------------------------
    // tool definitions
    // ------------------------------------------------------------------

    public List<ToolDefinition> tools() {
        var vocab = vocabulary();
        @SuppressWarnings("unchecked") List<String> segments = (List<String>) vocab.get("segments");
        @SuppressWarnings("unchecked") List<String> priceTiers = (List<String>) vocab.get("priceTiers");
        List<String> tiers = List.of("ENTRY", "MID", "HIGH", "FLAGSHIP");

        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(new ToolDefinition(
                "search_laptops",
                "Search the shop's live laptop inventory. Out-of-stock models are excluded. "
                        + "This is the ONLY way to learn what the shop sells — never name a model that did not "
                        + "come back from this call. Returns a compact summary of at most 6 machines; call "
                        + "get_laptop_details for the full specification of the ones you actually present.",
                "GET", "/laptops/search",
                object(props(
                        p("minPrice", number("Lower bound on list price, INR")),
                        p("maxPrice", number("Upper bound on list price, INR")),
                        p("minRam", integer("Minimum RAM in GB")),
                        p("minStorage", integer("Minimum storage in GB")),
                        p("storageType", str("Storage medium", List.of("SSD", "HDD"))),
                        p("os", str("Substring match on operating system, e.g. 'Windows'")),
                        p("refreshRateMin", integer("Minimum display refresh rate in Hz")),
                        p("maxWeightKg", number("Maximum weight in kg — use when portability matters")),
                        p("minBatteryHours", integer("Minimum rated battery hours")),
                        p("touchscreen", bool("Require a touchscreen")),
                        p("displayType", str("Panel type", List.of("FHD", "QHD", "4K", "OLED"))),
                        p("brand", str("Exact brand name", (List<String>) vocab.get("brands"))),
                        p("subBrand", str("Product line name, e.g. 'ROG', 'ThinkPad'")),
                        p("segment", str("Product line segment", segments)),
                        p("priceTier", str("Product line price tier", priceTiers)),
                        p("cpuBenchmarkTier", str("Required CPU class", tiers)),
                        p("gpuBrand", str("GPU maker, e.g. 'NVIDIA', 'AMD'")),
                        p("gpuBenchmarkTier", str("Required GPU class", tiers)),
                        p("discreteGpuRequired", bool("Only models with a dedicated GPU — set for gaming or GPU compute")),
                        p("minVramGb", integer("Minimum GPU VRAM in GB")),
                        p("modelNameContains", str("Substring match on model name — use when the customer names a model")),
                        p("limit", integer("Max results, 1-6, default 6")),
                        p("sort", str("Result ordering", (List<String>) vocab.get("sortOptions")))
                ), List.of()),
                List.of(),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "get_laptop_details",
                "Full specification for one laptop, including whitelisted extra specs. "
                        + "Call before presenting a model in detail or answering a specific spec question.",
                "GET", "/laptops/{id}",
                object(props(p("id", str("Laptop UUID from search_laptops"))), List.of("id")),
                List.of("NOT_FOUND"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "present_products",
                "Put products in front of the customer, with your reason for each one. Call this "
                        + "after search_laptops whenever you are recommending or showing options — the "
                        + "customer sees these as cards with your reason underneath. Give a real reason "
                        + "tied to what they told you (\"the only 144Hz screen under your budget\"), not a "
                        + "spec restatement. At most 4, and fewer is usually better. Ids are re-verified "
                        + "against the catalog, so an invented or since-sold-out id is rejected.",
                "POST", "/chat (internal)",
                object(props(
                        p("items", Map.of(
                                "type", "array",
                                "description", "1-4 products to show, best fit first",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "laptopId", str("Laptop UUID from search_laptops"),
                                                "reason", str("One sentence: why this one, for this customer")),
                                        "required", List.of("laptopId", "reason"))))
                ), List.of("items")),
                List.of("UNKNOWN_PRODUCT", "OUT_OF_STOCK", "TOO_MANY_PRESENTED", "BAD_ARGUMENTS"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "compare_laptops",
                "Aligned spec table for 2-5 laptops. Rows where the models differ are flagged and "
                        + "listed first — argue from those rows rather than reciting every spec.",
                "POST", "/laptops/compare",
                object(props(p("ids", array("string", "2-5 laptop UUIDs"))), List.of("ids")),
                List.of("NOT_FOUND", "VALIDATION_FAILED"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "search_catalog",
                "Device-type-agnostic search across every category the shop carries. Use this instead "
                        + "of search_laptops when the customer has not settled on a category.",
                "GET", "/catalog/search",
                object(props(
                        p("deviceType", str("Category to search", (List<String>) vocab.get("deviceTypes"))),
                        p("minPrice", number("Lower bound on list price, INR")),
                        p("maxPrice", number("Upper bound on list price, INR")),
                        p("minRamGb", integer("Minimum RAM in GB")),
                        p("minStorageGb", integer("Minimum storage in GB")),
                        p("brand", str("Exact brand name")),
                        p("segment", str("Product line segment", segments)),
                        p("priceTier", str("Product line price tier", priceTiers)),
                        p("cpuBenchmarkTier", str("Required processor class", tiers)),
                        p("limit", integer("Max results, 1-25, default 10"))
                ), List.of("deviceType")),
                List.of("NOT_FOUND"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "verify_identity",
                "Verify a customer's phone or email. Required before any discount or order. "
                        + "Ask for it naturally at the point it is needed, not up front.",
                "POST", "/identity/verify",
                object(props(p("identityKey", str("Verified phone number or email address"))),
                        List.of("identityKey")),
                List.of("IDENTITY_RATE_LIMITED"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "get_discount_limit",
                "Whether a laptop is negotiable at all, and the opening figure. "
                        + "maxPossiblePct in the response is merchant-internal — NEVER state it to the customer. "
                        + "The only number you may say out loud is one request_discount returned.",
                "GET", "/discounts/limit/{laptopId}",
                object(props(p("laptopId", str("Laptop UUID"))), List.of("laptopId")),
                List.of("NOT_FOUND"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "request_discount",
                "Ask the backend to approve a discount. The backend decides the figure; you decide "
                        + "whether and how to ask. Pass the number of negotiation rounds so far honestly — "
                        + "inflating it does not raise the cap beyond "
                        + props.getDiscount().getMaxRoundsCounted() + " rounds.",
                "POST", "/discounts/request",
                object(props(
                        p("laptopId", str("Laptop UUID")),
                        p("identityKey", str("Verified phone/email")),
                        p("requestedPct", number("Discount percentage the customer asked for")),
                        p("negotiationRounds", integer("How many times price has been discussed this conversation"))
                ), List.of("laptopId", "identityKey", "requestedPct")),
                List.of("IDENTITY_NOT_VERIFIED", "OUT_OF_STOCK", "NOT_FOUND"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "check_discount_offer",
                "Confirm an issued offer is still unredeemed, unexpired and owned by this identity.",
                "GET", "/discounts/{offerId}/valid",
                object(props(
                        p("offerId", str("Offer UUID from request_discount")),
                        p("identityKey", str("Verified phone/email"))
                ), List.of("offerId", "identityKey")),
                List.of("NOT_FOUND"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "create_order",
                "Close the sale. Holds one unit of stock and fixes the price. The backend re-derives "
                        + "the price from the stored offer — you cannot pass a price in, so never promise a "
                        + "figure this tool has not returned.",
                "POST", "/orders",
                object(props(
                        p("laptopId", str("Laptop UUID")),
                        p("identityKey", str("Verified phone/email")),
                        p("discountOfferId", str("Offer UUID, if a discount was agreed"))
                ), List.of("laptopId", "identityKey")),
                List.of("OUT_OF_STOCK", "OFFER_EXPIRED", "OFFER_ALREADY_REDEEMED",
                        "OFFER_IDENTITY_MISMATCH", "OFFER_LAPTOP_MISMATCH", "IDENTITY_NOT_VERIFIED"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "create_payment_link",
                "Generate a payment link for an order that is already CREATED.",
                "POST", "/orders/{id}/payment-link",
                object(props(p("id", str("Order UUID"))), List.of("id")),
                List.of("NOT_FOUND", "ORDER_NOT_PAYABLE"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "get_order_status",
                "Current status of an order: CREATED, PAID, FAILED or CANCELLED.",
                "GET", "/orders/{id}/status",
                object(props(p("id", str("Order UUID"))), List.of("id")),
                List.of("NOT_FOUND"),
                ToolDefinition.AUTHORITATIVE));

        tools.add(new ToolDefinition(
                "get_product_line_info",
                "General background on a product line — companion software, driver cadence, known "
                        + "quirks. " + WebInfoService.USAGE_RULE,
                "GET", "/webinfo/subbrand/{subBrandId}",
                object(props(
                        p("subBrandId", str("Sub-brand UUID from a laptop's subBrand.id")),
                        p("query", str("What you want to know, e.g. 'fan control software'"))
                ), List.of("subBrandId")),
                List.of("NOT_FOUND"),
                ToolDefinition.UNVERIFIED));

        return tools;
    }

    // ------------------------------------------------------------------
    // manifest
    // ------------------------------------------------------------------

    public Map<String, Object> manifest() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 1);
        m.put("principle",
                "The agent controls sales strategy and conversation. The backend controls business "
                        + "truth and execution. Any number, model name, stock figure or discount stated to a "
                        + "customer must have come from a tool response in this manifest.");
        m.put("infoSourceBoundary", Map.of(
                "backendOnly", List.of("price", "stock", "discount", "ram", "storage", "cpu", "gpu",
                        "benchmark tier", "display", "weight", "battery hours", "warranty months",
                        "whitelisted extra specs"),
                "webSearchable", List.of("companion software", "driver/firmware update cadence",
                        "OS compatibility notes", "known thermal or software quirks",
                        "general brand reputation"),
                "rule", "Anything in backendOnly that did not come from a tool response is a "
                        + "hallucination, including plausible-sounding round numbers."));
        m.put("negotiationPolicy", Map.of(
                "decidedBy", "backend",
                "openingPct", props.getDiscount().getBasePct(),
                "perRoundBonusPct", props.getDiscount().getPerRoundBonusPct(),
                "maxRoundsCounted", props.getDiscount().getMaxRoundsCounted(),
                "offerTtlMinutes", props.getDiscount().getOfferTtlMinutes(),
                "note", "The formula is deterministic: the same conversation shape yields the same "
                        + "number every time. Do not offer a discount before the customer raises price."));
        m.put("vocabulary", vocabulary());
        m.put("tools", tools());
        return m;
    }

    // ------------------------------------------------------------------
    // stage gating
    // ------------------------------------------------------------------

    /** Available from the first message: everything needed to find and show a machine. */
    private static final Set<String> DISCOVERY = Set.of(
            "search_laptops", "search_catalog", "get_laptop_details",
            "present_products", "compare_laptops", "get_product_line_info");

    /** Unlocked once a search has returned something to negotiate over. */
    private static final Set<String> NEGOTIATION = Set.of(
            "verify_identity", "get_discount_limit", "request_discount", "check_discount_offer");

    /** Unlocked only when there is a verified customer and a chosen machine. */
    private static final Set<String> CLOSING = Set.of(
            "create_order", "create_payment_link", "get_order_status");

    /**
     * The tools that are legal right now, rather than all thirteen every time.
     *
     * Two reasons, and the second is the one that matters.
     *
     * Cost: the full manifest serialises to roughly 2,000 tokens and was being
     * resent on every call — including each iteration of the tool loop. On a free
     * tier capped at 8,000 tokens per minute that is most of the budget spent
     * describing tools the agent cannot use yet, which is what a 429 mid-demo
     * actually is.
     *
     * Safety: an agent that cannot see create_order before an identity is
     * verified cannot attempt one. ToolExecutor already refuses it — this is the
     * second lock, not the first, and it removes the temptation rather than
     * punishing it. Same reasoning as leaving maxDiscountPct out of the summary
     * DTO: a capability the model never sees is one it can never talk itself
     * into.
     *
     * Gates open, never close. A conversation that reaches CLOSING keeps its
     * discovery tools, because "actually, show me something cheaper" is a normal
     * thing for a customer to say at the till.
     */
    public List<ToolDefinition> toolsFor(Conversation conv) {
        Set<String> allowed = new HashSet<>(DISCOVERY);

        boolean hasCandidates = conv.getCandidateIds() != null && !conv.getCandidateIds().isEmpty();
        if (hasCandidates || conv.getSelectedLaptop() != null || conv.identityKey() != null) {
            allowed.addAll(NEGOTIATION);
        }
        // Identity is the real gate, and the only one the backend itself enforces.
        // Requiring a selected laptop too would be tidier and would also break the
        // close: create_order takes a laptopId, and an agent that goes straight
        // from a search to the sale never sets selectedLaptop.
        if (conv.identityKey() != null) {
            allowed.addAll(CLOSING);
        }

        return tools().stream().filter(t -> allowed.contains(t.name())).toList();
    }

}
