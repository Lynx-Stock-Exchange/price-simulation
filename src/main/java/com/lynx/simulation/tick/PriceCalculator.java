package com.lynx.simulation.tick;

import com.lynx.simulation.model.PriceHistory;
import com.lynx.simulation.model.Stock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class PriceCalculator {

    // §6.2 formula: new_price = current + random_walk + order_pressure + event_component
    public PriceHistory calculate(Stock stock, int tick) {
        double current = stock.getCurrentPrice();

        // Random walk component — simulates natural price fluctuation
        double randomWalk = current * stock.getVolatility() * (Math.random() * 2 - 1)
                + stock.getTrendBias();

        // Order pressure component — placeholder until Person B provides Redis data
        double orderPressure = 0.0;

        // Event component — placeholder until Person B provides active event data
        double eventComponent = 0.0;

        // Calculate new price, minimum 0.01 to avoid negative prices
        double newPrice = Math.max(0.01,
                Math.round((current + randomWalk + orderPressure + eventComponent) * 100.0) / 100.0
        );

        log.debug("Tick #{} | {} | {} -> {}", tick, stock.getTicker(), current, newPrice);

        // Update stock's current price
        stock.setCurrentPrice(newPrice);
        stock.setHighPrice(Math.max(stock.getHighPrice(), newPrice));
        stock.setLowPrice(Math.min(stock.getLowPrice(), newPrice));

        // Build and return a price history record
        return PriceHistory.builder()
                .ticker(stock.getTicker())
                .open(stock.getOpenPrice())
                .high(stock.getHighPrice())
                .low(stock.getLowPrice())
                .close(newPrice)
                .volume(stock.getVolume())
                .timestamp(Instant.now())
                .build();
    }
}