package com.lynx.simulation.kafka.consumers;

import com.lynx.simulation.events.OrderPressureTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPlacedConsumer {

    private final OrderPressureTracker pressureTracker;

    @KafkaListener(topics = "orders.requests", groupId = "price-sim-consumer-group")
    public void consumeOrder(Map<String, Object> orderPayload) {
        try {
            String instrumentType = (String) orderPayload.get("instrument_type");
            String ticker = (String) orderPayload.get("instrument_id");
            String side = (String) orderPayload.get("side");
            Number quantityNum = (Number) orderPayload.get("quantity");

            if (quantityNum == null || ticker == null || side == null) {
                log.warn("Dropped malformed order payload missing critical fields.");
                return;
            }
            long quantity = quantityNum.longValue();

            if (!"STOCK".equalsIgnoreCase(instrumentType)) {
                return;
            }

            pressureTracker.recordOrderVolume(ticker, side, quantity);

            log.debug("Pressure recorded: {} shares of {} to {}", quantity, ticker, side);

        } catch (Exception e) {
            log.error("Failed to parse incoming order for pressure tracking: {}", e.getMessage());
        }
    }
}