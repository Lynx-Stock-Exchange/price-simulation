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

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("ticker", stock.getTicker());
        payload.put("name", stock.getName() != null ? stock.getName() : stock.getTicker());
        payload.put("sector", stock.getSector() != null ? stock.getSector() : "Unknown");
        payload.put("price", stock.getCurrentPrice());
        payload.put("open", stock.getOpenPrice());
        payload.put("high", stock.getHighPrice());
        payload.put("low", stock.getLowPrice());
        payload.put("change", change);
        payload.put("change_pct", changePct);
        payload.put("volume", stock.getVolume());
        payload.put("volatility", stock.getVolatility());
        payload.put("trend_bias", stock.getTrendBias());
        payload.put("event_weight", stock.getEventWeight());
        payload.put("momentum", stock.getMomentum());
        payload.put("market_time", simulatedClock.getFormattedTime());
        kafkaTemplate.send("stock.prices", stock.getTicker(), payload);
    }
}
