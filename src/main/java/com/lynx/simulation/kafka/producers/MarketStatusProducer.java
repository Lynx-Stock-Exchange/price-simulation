package com.lynx.simulation.kafka.producers;

import com.lynx.simulation.events.SimulatedClock;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketStatusProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimulatedClock simulatedClock;

    public void sendStatusChange(boolean isOpen) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", isOpen ? "MARKET_OPEN" : "MARKET_CLOSE");
        payload.put("is_open", isOpen);
        payload.put("market_time", simulatedClock.getFormattedTime());
        kafkaTemplate.send("market.events", UUID.randomUUID().toString(), payload);
    }
}
