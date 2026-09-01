package com.marketplace.analytics;

import com.marketplace.agent.state.Conversation;
import com.marketplace.agent.state.ConversationRepository;
import com.marketplace.agent.state.SalesStage;
import com.marketplace.laptop.entity.MarketplaceOrder;
import com.marketplace.laptop.entity.OrderStatus;
import com.marketplace.laptop.repository.DiscountOfferRepository;
import com.marketplace.laptop.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The numbers that answer "is this actually selling anything, or is it a
 * chatbot with a database attached?"
 *
 * Computed in Java over full table reads. At prototype scale that is fine and
 * keeps the logic readable; at real scale these become aggregate queries. The
 * point is that every figure here is derivable from rows the agent had no
 * ability to write directly — orders, offers and tool calls are all created by
 * the backend, never by the model.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final ConversationRepository conversations;
    private final OrderRepository orders;
    private final DiscountOfferRepository offers;

    public AnalyticsService(ConversationRepository conversations, OrderRepository orders,
                            DiscountOfferRepository offers) {
        this.conversations = conversations;
        this.orders = orders;
        this.offers = offers;
    }

    public List<ConversationSummaryDto> listConversations() {
        return conversations.findAll().stream()
                .sorted(Comparator.comparing(Conversation::getCreatedAt).reversed())
                .map(ConversationSummaryDto::from)
                .toList();
    }

    public Map<String, Object> summary() {
        List<Conversation> all = conversations.findAll();
        List<MarketplaceOrder> allOrders = orders.findAll();
        var allOffers = offers.findAll();

        long paid = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PAID).count();
        BigDecimal revenue = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.FAILED && o.getStatus() != OrderStatus.CANCELLED)
                .map(MarketplaceOrder::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountGiven = allOrders.stream()
                .filter(o -> o.getDiscountOffer() != null)
                .map(o -> o.getListPrice().subtract(o.getFinalPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long redeemed = allOffers.stream().filter(o -> o.isRedeemed()).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conversations", all.size());
        m.put("conversationsWithSearch", all.stream()
                .filter(c -> c.getCandidateIds() != null && !c.getCandidateIds().isEmpty()).count());
        m.put("conversationsWithObjection", all.stream()
                .filter(c -> c.getObjections() != null && !c.getObjections().isEmpty()).count());
        m.put("identitiesVerified", all.stream().filter(c -> c.identityKey() != null).count());

        m.put("orders", allOrders.size());
        m.put("ordersPaid", paid);
        m.put("conversionRate", ratio(allOrders.size(), all.size()));
        m.put("revenue", revenue);
        m.put("averageOrderValue", allOrders.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(allOrders.size()), 2, RoundingMode.HALF_UP));

        m.put("offersIssued", allOffers.size());
        m.put("offersRedeemed", redeemed);
        m.put("offerRedemptionRate", ratio(redeemed, allOffers.size()));
        m.put("averageApprovedPct", average(allOffers.stream()
                .map(o -> o.getApprovedPct() == null ? BigDecimal.ZERO : o.getApprovedPct()).toList()));
        m.put("totalDiscountGiven", discountGiven);

        m.put("averageQuestionsBeforeSearch", averageInt(all.stream()
                .map(Conversation::getQuestionsAsked).toList()));
        m.put("averageToolCallsPerConversation", averageInt(all.stream()
                .map(Conversation::getToolCallsTotal).toList()));
        m.put("totalToolCalls", all.stream().mapToInt(Conversation::getToolCallsTotal).sum());

        Map<String, Long> byStage = all.stream().collect(Collectors.groupingBy(
                c -> c.getStage().name(), LinkedHashMap::new, Collectors.counting()));
        for (SalesStage stage : SalesStage.values()) {
            byStage.putIfAbsent(stage.name(), 0L);
        }
        m.put("byStage", byStage);

        return m;
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageInt(List<Integer> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        int sum = values.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}