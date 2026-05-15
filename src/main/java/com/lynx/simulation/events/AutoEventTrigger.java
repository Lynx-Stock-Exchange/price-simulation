package com.lynx.simulation.events;

import com.lynx.simulation.model.ActiveMarketEvent;
import com.lynx.simulation.model.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoEventTrigger {

    private final MarketState marketState;

    private final Map<String, EventTracker> activeEvents = new ConcurrentHashMap<>();
    private static final String MARKET_WIDE_KEY = "GLOBAL_MARKET";

    /**
     * Called each tick. Handles expiration of active events only.
     * Triggering is now the responsibility of market-events service.
     */
    public void onTick() {
        if (!marketState.isOpen()) return;
        processExpirations();
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

    /**
     * Called by the market.events Kafka consumer when market-events publishes
     * an event (either auto-triggered or admin-triggered). Registers the event
     * in activeEvents so the price calculator can apply its magnitude.
     */
    public void registerEffect(Map<String, Object> payload) {
        String eventType   = stringVal(payload, "event_type", "UNKNOWN");
        String scope       = stringVal(payload, "scope", "MARKET");
        String target      = (String) payload.get("target");
        double magnitude   = toDouble(payload.get("magnitude"), 1.0);
        int durationTicks  = toInt(payload.get("duration_ticks"), 10);
        String headline    = stringVal(payload, "headline", eventType + " event triggered.");
        String eventId     = stringVal(payload, "event_id", UUID.randomUUID().toString());
        String triggeredBy = stringVal(payload, "triggered_by", "SYSTEM");

        ActiveMarketEvent event = new ActiveMarketEvent(
                eventId, eventType, scope, target, headline,
                magnitude, durationTicks, Instant.now(), triggeredBy);

        applyToActiveEvents(event);
    }

    private void applyToActiveEvents(ActiveMarketEvent event) {
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

        log.info("⚡ PRICE EFFECT REGISTERED: [{}] {} (Magnitude: {}, Duration: {} ticks)",
                event.scope(), event.headline(), event.magnitude(), event.durationTicks());
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

    private String stringVal(Map<String, Object> map, String key, String def) {
        Object v = map == null ? null : map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private double toDouble(Object val, double def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (NumberFormatException e) { return def; }
    }

    private int toInt(Object val, int def) {
        if (val == null) return def;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (NumberFormatException e) { return def; }
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
