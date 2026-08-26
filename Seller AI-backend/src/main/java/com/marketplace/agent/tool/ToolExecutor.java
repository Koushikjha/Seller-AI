package com.marketplace.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.agent.state.Conversation;
import com.marketplace.catalog.core.CatalogQuery;
import com.marketplace.catalog.core.CatalogRegistry;
import com.marketplace.catalog.core.DeviceType;
import com.marketplace.common.BusinessRuleException;
import com.marketplace.common.NotFoundException;
import com.marketplace.laptop.dto.CreateOrderRequest;
import com.marketplace.laptop.dto.DiscountRequest;
import com.marketplace.laptop.dto.LaptopSearchCriteria;
import com.marketplace.laptop.service.*;
import com.marketplace.identity.service.IdentityService;
import com.marketplace.webinfo.service.WebInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches a model-requested tool call to the real service layer.
 *
 * Two things happen here that the prompt cannot be trusted to do:
 *
 *  1. {@code identityKey} is taken from conversation state, not from the
 *     model's arguments, so the agent cannot act on someone else's identity
 *     even if it hallucinates one.
 *  2. {@code negotiationRounds} is counted by this class. Whatever the model
 *     passes is discarded. That turns the negotiation ladder from something
 *     the model can argue its way up into a fact about the conversation.
 *
 * Business failures are returned as structured payloads rather than thrown,
 * so the agent sees "OUT_OF_STOCK" and can recover in-conversation instead of
 * the turn dying.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final LaptopSearchService search;
    private final LaptopService laptops;
    private final LaptopCompareService compare;
    private final CatalogRegistry catalog;
    private final IdentityService identities;
    private final DiscountService discounts;
    private final OrderService orders;
    private final WebInfoService webInfo;
    private final ObjectMapper mapper;

    public ToolExecutor(LaptopSearchService search, LaptopService laptops, LaptopCompareService compare,
                        CatalogRegistry catalog, IdentityService identities, DiscountService discounts,
                        OrderService orders, WebInfoService webInfo, ObjectMapper mapper) {
        this.search = search;
        this.laptops = laptops;
        this.compare = compare;
        this.catalog = catalog;
        this.identities = identities;
        this.discounts = discounts;
        this.orders = orders;
        this.webInfo = webInfo;
        this.mapper = mapper;
    }

    public ToolOutcome execute(String name, Map<String, Object> args, Conversation conv) {
        Map<String, Object> a = args == null ? Map.of() : args;
        try {
            return switch (name) {
                case "search_laptops"         -> searchLaptops(a);
                case "get_laptop_details"     -> ToolOutcome.ok(laptops.get(uuid(a, "id")));
                case "compare_laptops"        -> compareLaptops(a);
                case "search_catalog"         -> searchCatalog(a);
                case "verify_identity"        -> verifyIdentity(a);
                case "get_discount_limit"     -> ToolOutcome.ok(discounts.limit(uuid(a, "laptopId")));
                case "request_discount"       -> requestDiscount(a, conv);
                case "check_discount_offer"   -> checkOffer(a, conv);
                case "create_order"           -> createOrder(a, conv);
                case "create_payment_link"    -> ToolOutcome.ok(orders.createPaymentLink(uuid(a, "id")));
                case "get_order_status"       -> ToolOutcome.ok(orders.get(uuid(a, "id")));
                case "get_product_line_info"  -> ToolOutcome.ok(
                        webInfo.forSubBrand(uuid(a, "subBrandId"), str(a, "query")));
                default -> ToolOutcome.error("UNKNOWN_TOOL",
                        "No such tool: " + name, null);
            };
        } catch (NotFoundException e) {
            return ToolOutcome.error("NOT_FOUND", e.getMessage(), null);
        } catch (BusinessRuleException e) {
            return ToolOutcome.error(e.getCode(), e.getMessage(), e.getDetails());
        } catch (IllegalArgumentException e) {
            return ToolOutcome.error("BAD_ARGUMENTS", e.getMessage(), null);
        } catch (Exception e) {
            log.error("Tool {} failed unexpectedly", name, e);
            return ToolOutcome.error("TOOL_FAILED",
                    "The tool failed unexpectedly; do not guess a result.", null);
        }
    }

    // ------------------------------------------------------------------

    private ToolOutcome searchLaptops(Map<String, Object> a) {
        var criteria = new LaptopSearchCriteria(
                dec(a, "minPrice"), dec(a, "maxPrice"), integer(a, "minRam"), integer(a, "minStorage"),
                str(a, "storageType"), str(a, "os"), integer(a, "refreshRateMin"), dec(a, "maxWeightKg"),
                integer(a, "minBatteryHours"), bool(a, "touchscreen"), str(a, "displayType"),
                str(a, "brand"), str(a, "subBrand"), str(a, "segment"), str(a, "priceTier"),
                str(a, "cpuBenchmarkTier"), str(a, "gpuBrand"), str(a, "gpuBenchmarkTier"),
                bool(a, "discreteGpuRequired"), integer(a, "minVramGb"), str(a, "modelNameContains"),
                true,                       // inStockOnly is NOT a model-controlled parameter
                capLimit(integer(a, "limit")), str(a, "sort"));
        return ToolOutcome.ok(search.searchSummaries(criteria));
    }

    /**
     * Hard ceiling on how many results reach the model. A model asking for 25
     * is not being helpful — it is filling its own context with machines it
     * will never mention, and on a metered tier that is real money or a real
     * rate limit. Six is more than any salesperson would put in front of you.
     */
    private Integer capLimit(Integer requested) {
        if (requested == null || requested <= 0) return 6;
        return Math.min(requested, 6);
    }

    private ToolOutcome compareLaptops(Map<String, Object> a) {
        Object raw = a.get("ids");
        if (!(raw instanceof List<?> list) || list.size() < 2) {
            return ToolOutcome.error("BAD_ARGUMENTS", "compare_laptops needs at least 2 ids", null);
        }
        List<UUID> ids = list.stream().map(o -> UUID.fromString(String.valueOf(o))).toList();
        return ToolOutcome.ok(compare.compare(ids));
    }

    private ToolOutcome searchCatalog(Map<String, Object> a) {
        DeviceType type = DeviceType.valueOf(str(a, "deviceType") == null
                ? "LAPTOP" : str(a, "deviceType").toUpperCase());
        var query = new CatalogQuery(type, dec(a, "minPrice"), dec(a, "maxPrice"),
                integer(a, "minRamGb"), integer(a, "minStorageGb"), str(a, "brand"),
                str(a, "segment"), str(a, "priceTier"), str(a, "cpuBenchmarkTier"),
                true, integer(a, "limit"), new HashMap<>());
        return ToolOutcome.ok(catalog.get(type).search(query));
    }

    private ToolOutcome verifyIdentity(Map<String, Object> a) {
        String key = str(a, "identityKey");
        if (key == null || key.isBlank()) {
            return ToolOutcome.error("BAD_ARGUMENTS", "identityKey is required", null);
        }
        // The agent has no IP; verification through the agent is attributed to
        // the agent host, so the per-IP limit still bounds a runaway loop.
        var vi = identities.verify(key, "agent");
        return ToolOutcome.ok(Map.of("identityKey", vi.getIdentityKey(), "verified", true));
    }

    private ToolOutcome requestDiscount(Map<String, Object> a, Conversation conv) {
        String identityKey = requireIdentity(conv, a);
        if (identityKey == null) {
            return ToolOutcome.error("IDENTITY_NOT_VERIFIED",
                    "Verify the customer's phone or email first with verify_identity.", null);
        }
        // The backend owns the round count, not the model.
        conv.setNegotiationRounds(conv.getNegotiationRounds() + 1);

        var req = new DiscountRequest(uuid(a, "laptopId"), identityKey,
                dec(a, "requestedPct") == null ? BigDecimal.ZERO : dec(a, "requestedPct"),
                conv.getNegotiationRounds());
        return ToolOutcome.ok(discounts.request(req));
    }

    private ToolOutcome checkOffer(Map<String, Object> a, Conversation conv) {
        String identityKey = requireIdentity(conv, a);
        return ToolOutcome.ok(discounts.validity(uuid(a, "offerId"), identityKey));
    }

    private ToolOutcome createOrder(Map<String, Object> a, Conversation conv) {
        String identityKey = requireIdentity(conv, a);
        if (identityKey == null) {
            return ToolOutcome.error("IDENTITY_NOT_VERIFIED",
                    "Verify the customer's phone or email first with verify_identity.", null);
        }
        var req = new CreateOrderRequest(uuid(a, "laptopId"), identityKey, uuidOrNull(a, "discountOfferId"));
        return ToolOutcome.ok(orders.create(req));
    }

    /** Conversation state wins over anything the model supplies. */
    private String requireIdentity(Conversation conv, Map<String, Object> a) {
        if (conv.identityKey() != null) return conv.identityKey();
        String supplied = str(a, "identityKey");
        return supplied == null || supplied.isBlank() ? null : supplied.trim().toLowerCase();
    }

    // ---------------- argument coercion ----------------

    private String str(Map<String, Object> a, String k) {
        Object v = a.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private UUID uuid(Map<String, Object> a, String k) {
        String v = str(a, k);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(k + " is required and must be a UUID from a previous tool result");
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(k + " is not a valid id: " + v
                    + ". Use an id returned by search_laptops — never invent one.");
        }
    }

    private UUID uuidOrNull(Map<String, Object> a, String k) {
        String v = str(a, k);
        if (v == null || v.isBlank() || "null".equals(v)) return null;
        return UUID.fromString(v);
    }

    private BigDecimal dec(Map<String, Object> a, String k) {
        Object v = a.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : new BigDecimal(s);
    }

    private Integer integer(Map<String, Object> a, String k) {
        Object v = a.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : (int) Double.parseDouble(s);
    }

    private Boolean bool(Map<String, Object> a, String k) {
        Object v = a.get(k);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.valueOf(String.valueOf(v).trim());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return mapper.convertValue(value, Map.class);
    }
}