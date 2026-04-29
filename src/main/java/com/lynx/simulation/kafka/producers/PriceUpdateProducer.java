package com.lynx.simulation.kafka.producers;

import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.model.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PriceUpdateProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SimulatedClock simulatedClock;

    public void send(Stock stock) {
        double change = stock.getCurrentPrice() - stock.getOpenPrice();
        double changePct = stock.getOpenPrice() > 0 ? (change / stock.getOpenPrice()) * 100 : 0.0;

        kafkaTemplate.send("price.update", stock.getTicker(), Map.of(
                "type", "PRICE_UPDATE",
                "payload", Map.of(
                        "ticker", stock.getTicker(),
                        "price", stock.getCurrentPrice(),
                        "change", change,
                        "change_pct", changePct,
                        "volume", stock.getVolume(),
                        "market_time", simulatedClock.getFormattedTime()
                )
        ));
    }
}
