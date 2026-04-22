package com.lynx.simulation.kafka.consumers;

import com.lynx.simulation.events.MarketState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommandConsumer {

    private final MarketState marketState;

    @KafkaListener(topics = "admin.commands", groupId = "price-sim-admin-group")
    public void consumeAdminCommand(Map<String, String> commandPayload) {
        try {
            String action = commandPayload.get("action");

            if (action == null) {
                log.warn("Dropped malformed admin command.");
                return;
            }

            switch (action.toUpperCase()) {
                case "OPEN_MARKET" -> {
                    marketState.setOpen(true);
                    log.warn("🟢 ADMIN COMMAND: Market is OPEN. Ticks resuming.");
                }
                case "CLOSE_MARKET" -> {
                    marketState.setOpen(false);
                    log.warn("🔴 ADMIN COMMAND: Market is CLOSED. Ticks halted.");
                }
                default -> log.info("Ignored unknown admin command: {}", action);
            }
        } catch (Exception e) {
            log.error("Failed to process admin command: {}", e.getMessage());
        }
    }
}