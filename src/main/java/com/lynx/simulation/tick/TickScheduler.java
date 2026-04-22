package com.lynx.simulation.tick;

import com.lynx.simulation.model.Stock;
import com.lynx.simulation.repository.StockRepository;
import com.lynx.simulation.repository.PriceHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TickScheduler {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceCalculator priceCalculator;

    private boolean marketOpen = true;
    private int currentTick = 0;

    @PostConstruct
    public void init() {
        List<Stock> stocks = stockRepository.findAll();
        stocks.forEach(stock -> {
            priceHistoryRepository
                    .findTopByTickerOrderByTimestampDesc(stock.getTicker())
                    .ifPresent(lastPrice -> {
                        stock.setCurrentPrice(lastPrice.getClose());
                        stockRepository.save(stock);
                        log.info("Restored {} price to {}", stock.getTicker(), lastPrice.getClose());
                    });
        });

        // Restore tick counter
        long count = (long) priceHistoryRepository
                .countByTickerAndTimestampAfter(
                        stocks.get(0).getTicker(),
                        stocks.get(0).getListedAt()
                );
        currentTick = (int) count;
        log.info("Restored tick counter to {}", currentTick);
    }


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

        List<Stock> stocks = stockRepository.findAll();
        stocks.forEach(stock -> {
            // Reset daily OHLC for next session
            stock.setOpenPrice(stock.getCurrentPrice());
            stock.setHighPrice(stock.getCurrentPrice());
            stock.setLowPrice(stock.getCurrentPrice());
            stock.setVolume(0);
            stockRepository.save(stock);
        });

        log.info("Daily OHLC reset complete, market ready for next session");

        log.info("Market closed");
    }
}