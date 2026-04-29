package com.lynx.simulation.tick;

import com.lynx.simulation.events.AutoEventTrigger;
import com.lynx.simulation.events.OrderPressureTracker;
import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.model.PriceHistory;
import com.lynx.simulation.model.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceCalculator {

    private final OrderPressureTracker orderPressureTracker;
    private final AutoEventTrigger autoEventTrigger;
    private final SimulatedClock simulatedClock;

    // §6.2 formula: new_price = current + random_walk + order_pressure + event_component
    public PriceHistory calculate(Stock stock, int tick) {
        double current = stock.getCurrentPrice();

        // Random walk component — simulates natural price fluctuation
        double randomWalk = current * stock.getVolatility() * (ThreadLocalRandom.current().nextDouble() * 2 - 1)
                + stock.getTrendBias();

        // Order pressure component — pressure_ratio clamped to [-0.05, 0.05] per §6.2
        OrderPressureTracker.PressureData pressure = orderPressureTracker.getAndResetPressure(stock.getTicker());
        long totalVol = pressure.getTotalVolume();
        double pressureRatio = totalVol == 0 ? 0.0 : Math.max(-0.05, Math.min(0.05,
                (double)(pressure.buyVolume() - pressure.sellVolume()) / totalVol
        ));
        double orderPressure = pressureRatio * current * stock.getMomentum();
        stock.setVolume(stock.getVolume() + totalVol);

        // Event component — amplifies random walk scaled by stock's event weight per §6.2 + §3.1
        double activeMagnitude = autoEventTrigger.getActiveMagnitudeFor(stock);
        double eventComponent = activeMagnitude != 1.0
                ? randomWalk * (activeMagnitude - 1.0) * stock.getEventWeight()
                : 0.0;

        // Calculate new price, minimum 0.01 to avoid negative prices
        double newPrice = Math.max(0.01,
                Math.round((current + randomWalk + orderPressure + eventComponent) * 100.0) / 100.0
        );

        log.debug("Tick #{} | {} | {} -> {}", tick, stock.getTicker(), current, newPrice);

        // Update stock's current price
        stock.setCurrentPrice(newPrice);
        stock.setHighPrice(Math.max(stock.getHighPrice(), newPrice));
        stock.setLowPrice(Math.min(stock.getLowPrice(), newPrice));

        // PriceHistory records per-tick volume (totalVol), not the cumulative daily stock.getVolume()
        return PriceHistory.builder()
                .ticker(stock.getTicker())
                .open(current)
                .high(Math.max(current, newPrice))
                .low(Math.min(current, newPrice))
                .close(newPrice)
                .volume(totalVol)
                .timestamp(simulatedClock.getSimulatedInstant())
                .build();
    }
}
