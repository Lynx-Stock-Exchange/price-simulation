package com.lynx.simulation.kafka.consumers;

import com.lynx.simulation.events.AutoEventTrigger;
import com.lynx.simulation.tick.OrderMatchingEngine;
import com.lynx.simulation.tick.TickScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommandConsumer {

    private final TickScheduler tickScheduler;
    private final AutoEventTrigger autoEventTrigger;
    private final OrderMatchingEngine orderMatchingEngine;

    @KafkaListener(topics = "admin.commands", groupId = "price-sim-admin-group")
    public void consumeAdminCommand(Map<String, Object> commandPayload) {
        try {
            String action = (String) commandPayload.get("action");

            if (action == null) {
                log.warn("Dropped malformed admin command.");
                return;
            }

            switch (action.toUpperCase()) {
                case "OPEN_MARKET" -> {
                    tickScheduler.openMarket();
                    log.warn("🟢 ADMIN COMMAND: Market is OPEN. Ticks resuming.");
                }
                case "CLOSE_MARKET" -> {
                    tickScheduler.closeMarket();
                    log.warn("🔴 ADMIN COMMAND: Market is CLOSED. Ticks halted.");
                }
                case "TRIGGER_EVENT" -> {
                    String eventType = (String) commandPayload.get("event_type");
                    if (eventType == null) {
                        log.warn("TRIGGER_EVENT command missing required field: event_type");
                        return;
                    }
                    autoEventTrigger.triggerEventByType(eventType);
                    log.warn("🎯 ADMIN COMMAND: Triggered market event of type {}", eventType);
                }
                case "UPDATE_FEE" -> {
                    Object feeRateRaw = commandPayload.get("fee_rate");
                    if (feeRateRaw == null) {
                        log.warn("UPDATE_FEE command missing required field: fee_rate");
                        return;
                    }
                    try {
                        double newFeeRate = Double.parseDouble(feeRateRaw.toString());
                        orderMatchingEngine.setFeeRate(newFeeRate);
                        log.warn("💰 ADMIN COMMAND: Fee rate updated to {}", newFeeRate);
                    } catch (NumberFormatException e) {
                        log.warn("UPDATE_FEE command has invalid fee_rate value: {}", feeRateRaw);
                    }
                }
                default -> log.info("Ignored unknown admin command: {}", action);
            }
        } catch (Exception e) {
            log.error("Failed to process admin command: {}", e.getMessage());
        }
    }
}