package com.marketplace.laptop.service;

import com.marketplace.common.BusinessRuleException;
import com.marketplace.common.NotFoundException;
import com.marketplace.identity.service.IdentityService;
import com.marketplace.laptop.dto.CreateOrderRequest;
import com.marketplace.laptop.dto.DiscountOfferDto;
import com.marketplace.laptop.dto.OrderDto;
import com.marketplace.laptop.entity.DiscountOffer;
import com.marketplace.laptop.entity.Laptop;
import com.marketplace.laptop.entity.MarketplaceOrder;
import com.marketplace.laptop.entity.OrderStatus;
import com.marketplace.laptop.repository.LaptopRepository;
import com.marketplace.laptop.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final LaptopRepository laptops;
    private final IdentityService identities;
    private final DiscountService discounts;
    private final PaymentGateway gateway;

    public OrderService(OrderRepository orders, LaptopRepository laptops, IdentityService identities,
                        DiscountService discounts, PaymentGateway gateway) {
        this.orders = orders;
        this.laptops = laptops;
        this.identities = identities;
        this.discounts = discounts;
        this.gateway = gateway;
    }

    /**
     * The close. Price is re-derived here from the persisted offer -- the agent
     * cannot pass a price in, so it cannot promise one the backend never approved.
     */
    public OrderDto create(CreateOrderRequest req) {
        identities.require(req.identityKey());
        String key = req.identityKey().trim().toLowerCase();

        Laptop laptop = laptops.findByIdForUpdate(req.laptopId())
                .orElseThrow(() -> new NotFoundException("Laptop", req.laptopId()));

        if (laptop.getStockQty() <= 0) {
            throw new BusinessRuleException("OUT_OF_STOCK",
                    "This laptop just went out of stock",
                    Map.of("laptopId", laptop.getId(), "modelName", laptop.getModelName()));
        }

        BigDecimal listPrice = laptop.getBasePrice();
        BigDecimal discountPct = BigDecimal.ZERO;
        DiscountOffer offer = null;

        if (req.discountOfferId() != null) {
            offer = discounts.consume(req.discountOfferId(), laptop.getId(), key);
            discountPct = offer.getApprovedPct() == null ? BigDecimal.ZERO : offer.getApprovedPct();
        }

        BigDecimal finalPrice = DiscountOfferDto.applyPct(listPrice, discountPct);

        laptop.setStockQty(laptop.getStockQty() - 1);
        laptops.save(laptop);

        MarketplaceOrder order = new MarketplaceOrder();
        order.setLaptop(laptop);
        order.setIdentityKey(key);
        order.setListPrice(listPrice);
        order.setDiscountPct(discountPct);
        order.setFinalPrice(finalPrice);
        order.setDiscountOffer(offer);
        order.setStatus(OrderStatus.CREATED);

        return OrderDto.from(orders.save(order));
    }

    public OrderDto createPaymentLink(UUID orderId) {
        MarketplaceOrder order = require(orderId);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleException("ORDER_NOT_PAYABLE",
                    "Order is in status " + order.getStatus() + " and cannot take a new payment link", null);
        }
        var link = gateway.createPaymentLink(order);
        order.setPaymentRef(link.providerRef());
        order.setPaymentLink(link.url());
        return OrderDto.from(orders.save(order));
    }

    /** Demo/webhook hook: mark an order paid or failed. */
    public OrderDto settle(UUID orderId, boolean paid) {
        MarketplaceOrder order = require(orderId);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleException("ORDER_ALREADY_SETTLED",
                    "Order is already " + order.getStatus(), null);
        }
        if (paid) {
            order.setStatus(OrderStatus.PAID);
        } else {
            order.setStatus(OrderStatus.FAILED);
            // release the held unit
            Laptop laptop = order.getLaptop();
            laptop.setStockQty(laptop.getStockQty() + 1);
            laptops.save(laptop);
        }
        return OrderDto.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public OrderDto get(UUID orderId) {
        return OrderDto.from(require(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> byIdentity(String identityKey) {
        return orders.findByIdentityKeyOrderByCreatedAtDesc(identityKey.trim().toLowerCase())
                .stream().map(OrderDto::from).toList();
    }

    @Transactional(readOnly = true)
    public String gatewayName() {
        return gateway.name();
    }

    private MarketplaceOrder require(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}
