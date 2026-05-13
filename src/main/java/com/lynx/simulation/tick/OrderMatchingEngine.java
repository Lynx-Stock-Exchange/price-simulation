package com.lynx.simulation.tick;

import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.kafka.producers.OrderUpdateProducer;
import com.lynx.simulation.model.Order;
import com.lynx.simulation.model.OrderStatus;
import com.lynx.simulation.model.Stock;
import com.lynx.simulation.model.Trade;
import com.lynx.simulation.repository.OrderRepository;
import com.lynx.simulation.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingEngine {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final OrderUpdateProducer orderUpdateProducer;
    private final SimulatedClock simulatedClock;

    @Value("${simulation.fee-rate:0.001}")
    private double feeRate;

    public void setFeeRate(double feeRate) {
        this.feeRate = feeRate;
        log.info("Fee rate updated to {}", feeRate);
    }

    public void matchOrders(Stock stock) {
        double currentPrice = stock.getCurrentPrice();
        Instant now = simulatedClock.getSimulatedInstant();

        List<Order> orders = orderRepository.findByInstrumentIdAndStatusIn(
                stock.getTicker(),
                List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
        );

        orders.forEach(order -> {
            if (order.getExpiresAt() != null && now.isAfter(order.getExpiresAt())) {
                expireSingleOrder(order, now);
            } else if (shouldFill(order, currentPrice)) {
                fill(order, currentPrice);
            }
        });
    }

    private void expireSingleOrder(Order order, Instant now) {
        order.setStatus(OrderStatus.EXPIRED);
        order.setUpdatedAt(now);
        orderRepository.save(order);
        orderUpdateProducer.sendOrderUpdate(order);
        log.info("Order expired intra-day: {}", order.getOrderId());
    }

    public void expireOpenOrders() {
        List<Order> openOrders = orderRepository.findByStatusIn(
                List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
        );
        if (openOrders.isEmpty()) return;

        Instant now = simulatedClock.getSimulatedInstant();
        // §6.4: LIMIT orders → EXPIRED; MARKET orders → REJECTED at close
        openOrders.forEach(order -> {
            order.setStatus("MARKET".equalsIgnoreCase(order.getOrderType())
                    ? OrderStatus.REJECTED
                    : OrderStatus.EXPIRED);
            order.setUpdatedAt(now);
        });
        orderRepository.saveAll(openOrders);
        openOrders.forEach(orderUpdateProducer::sendOrderUpdate);
        long expired = openOrders.stream().filter(o -> o.getStatus() == OrderStatus.EXPIRED).count();
        long rejected = openOrders.stream().filter(o -> o.getStatus() == OrderStatus.REJECTED).count();
        log.info("Market close: {} limit orders EXPIRED, {} market orders REJECTED", expired, rejected);
    }

    private boolean shouldFill(Order order, double currentPrice) {
        return switch (order.getOrderType().toUpperCase()) {
            case "MARKET" -> true;
            case "LIMIT" -> order.getSide() == Order.Side.BUY
                    ? currentPrice <= order.getLimitPrice()
                    : currentPrice >= order.getLimitPrice();
            default -> false;
        };
    }

    private void fill(Order order, double currentPrice) {
        // Limit orders execute at their stated limit price per §6.3; market orders at current price
        double fillPrice = "LIMIT".equalsIgnoreCase(order.getOrderType())
                ? order.getLimitPrice()
                : currentPrice;

        int prevFilled = order.getFilledQuantity();
        int newFilled = order.getQuantity() - prevFilled;
        double thisFee = fillPrice * newFilled * feeRate;

        // Weighted average across any prior partial fill
        double prevAvg = order.getAverageFillPrice() != null ? order.getAverageFillPrice() : 0.0;
        double newAvg = prevFilled == 0
                ? fillPrice
                : (prevAvg * prevFilled + fillPrice * newFilled) / order.getQuantity();

        double prevFee = order.getExchangeFee() != null ? order.getExchangeFee() : 0.0;
        Instant executedAt = simulatedClock.getSimulatedInstant();

        order.setFilledQuantity(order.getQuantity());
        order.setAverageFillPrice(newAvg);
        order.setExchangeFee(prevFee + thisFee);
        order.setStatus(OrderStatus.FILLED);
        order.setUpdatedAt(executedAt);
        orderRepository.save(order);

        tradeRepository.save(Trade.builder()
                .tradeId(UUID.randomUUID().toString())
                .orderId(order.getOrderId())
                .platformId(order.getPlatformId())
                .platformUserId(order.getPlatformUserId())
                .instrumentType(order.getInstrumentType())
                .instrumentId(order.getInstrumentId())
                .side(order.getSide())
                .quantity(newFilled)
                .price(fillPrice)
                .exchangeFee(thisFee)
                .executedAt(executedAt)
                .build());

        orderUpdateProducer.sendOrderUpdate(order);
        log.info("Order filled: {} {} x{} @ {}", order.getSide(), order.getInstrumentId(), newFilled, fillPrice);
    }
}
