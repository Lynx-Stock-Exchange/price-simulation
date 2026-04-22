package com.lynx.simulation.events;

import com.lynx.simulation.model.ActiveMarketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoEventTrigger {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${simulation.auto-event-probability:0.005}")
    private double eventProbability;

    private final Map<String, EventTracker> activeEvents = new ConcurrentHashMap<>();
    private static final String MARKET_WIDE_KEY = "GLOBAL_MARKET";

    @Scheduled(fixedRateString = "${simulation.tick-rate-ms:1000}")
    public void onTick() {
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
        EventTracker tracker = new EventTracker(newEvent);

        if ("MARKET".equalsIgnoreCase(newEvent.scope())) {
            if (activeEvents.containsKey(MARKET_WIDE_KEY)) {
                log.debug("A market-wide event is already active. Dropping new global event.");
                return;
            }
            activeEvents.put(MARKET_WIDE_KEY, tracker);
        } else {
            activeEvents.put(newEvent.target(), tracker);
        }

        kafkaTemplate.send("market.events", newEvent.eventId(), newEvent);

        log.info("🚨 MARKET SHOCK STARTED: [{}] {} (Magnitude: {}, Duration: {} ticks)", newEvent.scope(), newEvent.headline(), newEvent.magnitude(), newEvent.durationTicks());
    }

    // TODO: change this so it draws from the seed provided
    private ActiveMarketEvent drawRandomEventFromSeed() {
        boolean isGlobal = ThreadLocalRandom.current().nextBoolean();

        if (isGlobal) {
            return new ActiveMarketEvent(
                    UUID.randomUUID().toString(), "MARKET", "GLOBAL",
                    "Global interest rates slashed unexpectedly!", 1.05, 10
            );
        } else {
            return new ActiveMarketEvent(
                    UUID.randomUUID().toString(), "SECTOR", "TECH",
                    "Silicon shortage hits hardware manufacturers.", 0.97, 15
            );
        }
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