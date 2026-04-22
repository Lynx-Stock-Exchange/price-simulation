package com.lynx.simulation.tick;

import com.lynx.simulation.repository.StockRepository;
import com.lynx.simulation.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TickScheduler {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceCalculator priceCalculator;

    private boolean marketOpen = true;
    private int currentTick = 0;

    @Scheduled(fixedRateString = "${simulation.tick-rate-ms}")
    public void tick() {
        if (!marketOpen) return;

        currentTick++;
        log.info("Tick #{} started", currentTick);

        // 1. Load all stocks from the database
        var stocks = stockRepository.findAll();

        // 2. Calculate new price for each stock and persist to price_history
        stocks.forEach(stock -> {
            var updatedPrice = priceCalculator.calculate(stock, currentTick);
            priceHistoryRepository.save(updatedPrice);
        });
    }

    public void openMarket() {
        this.marketOpen = true;
        log.info("Market opened");
    }

    public void closeMarket() {
        this.marketOpen = false;
        log.info("Market closed");
    }
}