package com.lynx.simulation.tick;

import com.lynx.simulation.kafka.producers.MarketEventProducer;
import com.lynx.simulation.model.Order;
import com.lynx.simulation.model.OrderStatus;
import com.lynx.simulation.model.Stock;
import com.lynx.simulation.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingEngine {

    private final OrderRepository orderRepository;
    private final MarketEventProducer marketEventProducer;

    private static final double FEE_RATE = 0.001; // 0.1% of trade value

    public void matchOrders(Stock stock) {
        double currentPrice = stock.getCurrentPrice();

        List<Order> orders = orderRepository.findByInstrumentIdAndStatusIn(
                stock.getTicker(),
                List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED)
        );

        orders.forEach(order -> {
            if (shouldFill(order, currentPrice)) {
                fill(order, currentPrice);
            }
        });
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
        int remaining = order.getQuantity() - order.getFilledQuantity();
        order.setFilledQuantity(order.getQuantity());
        order.setAverageFillPrice(currentPrice);
        order.setExchangeFee(currentPrice * remaining * FEE_RATE);
        order.setStatus(OrderStatus.FILLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        marketEventProducer.sendOrderUpdate(order);
        log.info("Order filled: {} {} x{} @ {}", order.getSide(), order.getInstrumentId(), remaining, currentPrice);
    }
}
