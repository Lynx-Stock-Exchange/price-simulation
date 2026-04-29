package com.lynx.simulation.kafka.producers;

import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderUpdateProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimulatedClock simulatedClock;

    public void sendOrderUpdate(Order order) {
        // HashMap used instead of Map.of() to safely handle null fill price/fee (e.g. expired orders)
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", order.getOrderId());
        payload.put("status", order.getStatus());
        payload.put("filled_quantity", order.getFilledQuantity());
        payload.put("average_fill_price", order.getAverageFillPrice() != null ? order.getAverageFillPrice() : 0.0);
        payload.put("exchange_fee", order.getExchangeFee() != null ? order.getExchangeFee() : 0.0);
        payload.put("market_time", simulatedClock.getFormattedTime());

        kafkaTemplate.send("order.update", order.getOrderId(), Map.of(
                "type", "ORDER_UPDATE",
                "payload", payload
        ));
        log.info("Order update sent: {} -> {}", order.getOrderId(), order.getStatus());
    }
}
