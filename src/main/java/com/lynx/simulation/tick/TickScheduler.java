package com.lynx.simulation.tick;

import com.lynx.simulation.events.MarketState;
import com.lynx.simulation.kafka.producers.PriceUpdateProducer;
import com.lynx.simulation.model.Stock;
import com.lynx.simulation.repository.StockRepository;
import com.lynx.simulation.repository.PriceHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class TickScheduler {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceCalculator priceCalculator;
    private final PriceUpdateProducer priceUpdateProducer;
    private final OrderMatchingEngine orderMatchingEngine;
    private final MarketState marketState;

    private final Map<String, Stock> stockCache = new LinkedHashMap<>();
    private int currentTick = 0;

    @PostConstruct
    public void init() {
        reloadCache();
    }

    public void reloadCache() {
        stockCache.clear();
        List<Stock> stocks = stockRepository.findAll();

        if (stocks.isEmpty()) {
            log.warn("No stocks found, skipping state restore");
            return;
        }

        stocks.forEach(stock -> {
            priceHistoryRepository
                    .findTopByTickerOrderByTimestampDesc(stock.getTicker())
                    .ifPresent(lastPrice -> stock.setCurrentPrice(lastPrice.getClose()));
            stockCache.put(stock.getTicker(), stock);
        });

        long count = (long) priceHistoryRepository
                .countByTickerAndTimestampAfter(
                        stocks.get(0).getTicker(),
                        stocks.get(0).getListedAt()
                );
        currentTick = (int) count;
        log.info("Restored tick counter to {}, {} stocks loaded", currentTick, stockCache.size());
        marketState.setOpen(true);
        log.info("Market opened automatically on startup");
    }

    @Scheduled(fixedRateString = "${simulation.tick-rate-ms}")
    public void tick() {
        if (!marketState.isOpen()) return;

        currentTick++;
        log.info("Tick #{} | prices: {}", currentTick,
                stockCache.values().stream()
                        .map(s -> s.getTicker() + "=" + s.getCurrentPrice())
                        .toList());

        stockCache.values().forEach(stock -> {
            var updatedPrice = priceCalculator.calculate(stock, currentTick);
            orderMatchingEngine.matchOrders(stock);
            priceHistoryRepository.save(updatedPrice);
            priceUpdateProducer.send(stock, currentTick);
        });
    }

    public void openMarket() {
        marketState.setOpen(true);
        log.info("Market opened");
    }

    public void closeMarket() {
        marketState.setOpen(false);

        stockCache.values().forEach(stock -> {
            stock.setOpenPrice(stock.getCurrentPrice());
            stock.setHighPrice(stock.getCurrentPrice());
            stock.setLowPrice(stock.getCurrentPrice());
            stock.setVolume(0);
            stockRepository.save(stock);
        });

        log.info("Daily OHLC reset complete, market closed");
    }
}
