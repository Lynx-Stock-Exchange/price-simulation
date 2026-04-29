package com.lynx.simulation.events;

import com.lynx.simulation.config.EventDefinitionConfig;
import com.lynx.simulation.model.ActiveMarketEvent;
import com.lynx.simulation.model.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoEventTrigger {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventDefinitionConfig eventConfig;
    private final MarketState marketState;
    private final SimulatedClock simulatedClock;

    @Value("${simulation.auto-event-probability:0.005}")
    private double eventProbability;

    private final Map<String, EventTracker> activeEvents = new ConcurrentHashMap<>();
    private static final String MARKET_WIDE_KEY = "GLOBAL_MARKET";

    public void onTick() {
        if (!marketState.isOpen()) return;
        processExpirations();
        evaluateRandomEvent();
    }

    private void processExpirations() {
        activeEvents.values().removeIf(tracker -> {
            tracker.ticksRemaining--;
            if (tracker.ticksRemaining <= 0) {
                log.info("✅ MARKET SHOCK ENDED: [{}] {}", tracker.event.scope(), tracker.event.headline());
                return true;
            }
            return false;
        });
    }

    private void evaluateRandomEvent() {
        if (ThreadLocalRandom.current().nextDouble() <= eventProbability) {
            triggerEvent();
        }
    }

    private void triggerEvent() {
        ActiveMarketEvent newEvent = drawRandomEventFromSeed();
        if (newEvent == null) return;
        fireEvent(newEvent);
    }

    public void triggerEventByType(String eventType) {
        List<EventDefinitionConfig.EventDefinition> defs = eventConfig.getDefinitions();
        EventDefinitionConfig.EventDefinition def = defs.stream()
                .filter(d -> d.getEventType().equalsIgnoreCase(eventType))
                .findFirst()
                .orElse(null);

        if (def == null) {
            log.warn("No event definition found for type: {}", eventType);
            return;
        }

        List<String> headlines = def.getHeadlines();
        String headline = headlines.isEmpty()
                ? def.getEventType() + " event triggered."
                : headlines.get(ThreadLocalRandom.current().nextInt(headlines.size()));

        fireEvent(new ActiveMarketEvent(
                UUID.randomUUID().toString(),
                def.getEventType(),
                def.getScope(),
                def.getTarget(),
                headline,
                def.getMagnitude(),
                def.getDurationTicks(),
                Instant.now(),
                "ADMIN"
        ));
    }

    private void fireEvent(ActiveMarketEvent event) {
        EventTracker tracker = new EventTracker(event);

        if ("MARKET".equalsIgnoreCase(event.scope())) {
            if (activeEvents.containsKey(MARKET_WIDE_KEY)) {
                log.debug("A market-wide event is already active. Dropping new global event.");
                return;
            }
            activeEvents.put(MARKET_WIDE_KEY, tracker);
        } else {
            if (event.target() == null) {
                log.warn("Non-MARKET event '{}' has null target — dropping", event.eventType());
                return;
            }
            activeEvents.put(event.target(), tracker);
        }

        // Build envelope matching the spec §5.3 MARKET_EVENT wire format
        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", event.eventId());
        payload.put("event_type", event.eventType());
        payload.put("headline", event.headline());
        payload.put("scope", event.scope());
        payload.put("target", event.target()); // null for MARKET scope per §3.6
        payload.put("magnitude", event.magnitude());
        payload.put("duration_ticks", event.durationTicks());
        payload.put("market_time", simulatedClock.getFormattedTime());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", "MARKET_EVENT");
        envelope.put("payload", payload);

        kafkaTemplate.send("market.events", event.eventId(), envelope);
        log.info("🚨 MARKET SHOCK STARTED: [{}] {} (Magnitude: {}, Duration: {} ticks)", event.scope(), event.headline(), event.magnitude(), event.durationTicks());
    }

    private ActiveMarketEvent drawRandomEventFromSeed() {
        List<EventDefinitionConfig.EventDefinition> defs = eventConfig.getDefinitions();
        if (defs.isEmpty()) {
            log.warn("No event definitions configured — skipping event trigger.");
            return null;
        }

        // Weighted random selection
        double totalWeight = defs.stream().mapToDouble(EventDefinitionConfig.EventDefinition::getWeight).sum();
        double rand = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0;
        EventDefinitionConfig.EventDefinition chosen = defs.getLast();
        for (EventDefinitionConfig.EventDefinition def : defs) {
            cumulative += def.getWeight();
            if (rand < cumulative) {
                chosen = def;
                break;
            }
        }

        List<String> headlines = chosen.getHeadlines();
        String headline = headlines.isEmpty()
                ? chosen.getEventType() + " event triggered."
                : headlines.get(ThreadLocalRandom.current().nextInt(headlines.size()));

        return new ActiveMarketEvent(
                UUID.randomUUID().toString(),
                chosen.getEventType(),
                chosen.getScope(),
                chosen.getTarget(),
                headline,
                chosen.getMagnitude(),
                chosen.getDurationTicks(),
                Instant.now(),
                "SYSTEM"
        );
    }

    public double getActiveMagnitudeFor(Stock stock) {
        double combined = 1.0;

        EventTracker market = activeEvents.get(MARKET_WIDE_KEY);
        if (market != null) combined *= market.event.magnitude();

        EventTracker sector = activeEvents.get(stock.getSector().toUpperCase());
        if (sector != null) combined *= sector.event.magnitude();

        EventTracker stockLevel = activeEvents.get(stock.getTicker());
        if (stockLevel != null) combined *= stockLevel.event.magnitude();

        return combined;
    }

    private static class EventTracker {
        final ActiveMarketEvent event;
        int ticksRemaining;

        EventTracker(ActiveMarketEvent event) {
            this.event = event;
            this.ticksRemaining = event.durationTicks();
        }
    }
}