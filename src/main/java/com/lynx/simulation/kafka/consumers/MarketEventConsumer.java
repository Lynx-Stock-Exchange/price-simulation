package com.lynx.simulation.kafka.consumers;

import com.lynx.simulation.events.AutoEventTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Consumes market.events published by the market-events service and registers
 * the active event in AutoEventTrigger so the price calculator can apply the
 * magnitude multiplier to affected stocks.
 *
 * Messages are WebSocketEnvelope<MarketEventPayload> JSON:
 *   { "type": "MARKET_EVENT", "payload": { event_id, event_type, scope, target, magnitude, duration_ticks, ... } }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketEventConsumer {

    private final AutoEventTrigger autoEventTrigger;

    @KafkaListener(topics = "market.events", groupId = "price-sim-market-events-group")
    public void onMarketEvent(Map<String, Object> envelope) {
        try {
            Object payloadRaw = envelope.get("payload");
            if (!(payloadRaw instanceof Map<?, ?> rawMap)) {
                log.warn("market.events message missing or invalid 'payload' field");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) rawMap;

            autoEventTrigger.registerEffect(payload);
        } catch (Exception e) {
            log.error("Failed to process market.events message: {}", e.getMessage());
        }
    }
}
