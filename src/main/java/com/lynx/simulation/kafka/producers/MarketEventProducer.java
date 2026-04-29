package com.lynx.simulation.kafka.producers;

import com.lynx.simulation.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderUpdate(Order order) {
        kafkaTemplate.send("order.update", order.getOrderId(), Map.of(
                "type", "ORDER_UPDATE",
                "payload", Map.of(
                        "order_id", order.getOrderId(),
                        "status", order.getStatus(),
                        "filled_quantity", order.getFilledQuantity(),
                        "average_fill_price", order.getAverageFillPrice(),
                        "exchange_fee", order.getExchangeFee(),
                        "market_time", order.getUpdatedAt()
                )
        ));
        log.info("Order update sent: {} -> {}", order.getOrderId(), order.getStatus());
    }
}