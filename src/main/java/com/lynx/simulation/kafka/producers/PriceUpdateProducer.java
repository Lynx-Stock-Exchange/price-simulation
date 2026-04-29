package com.lynx.simulation.kafka.producers;

import com.lynx.simulation.model.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PriceUpdateProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(Stock stock, int tick) {
        kafkaTemplate.send("price.update", stock.getTicker(), Map.of(
                "type", "PRICE_UPDATE",
                "payload", Map.of(
                        "ticker", stock.getTicker(),
                        "price", stock.getCurrentPrice(),
                        "change", stock.getCurrentPrice() - stock.getOpenPrice(),
                        "change_pct", ((stock.getCurrentPrice() - stock.getOpenPrice()) / stock.getOpenPrice()) * 100,
                        "volume", stock.getVolume(),
                        "market_time", Instant.now().toString()
                )
        ));
    }
}