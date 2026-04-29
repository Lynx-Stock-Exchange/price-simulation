package com.lynx.simulation.tick;

import com.lynx.simulation.events.AutoEventTrigger;
import com.lynx.simulation.events.MarketState;
import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.kafka.producers.PriceUpdateProducer;
import com.lynx.simulation.model.Stock;
import com.lynx.simulation.repository.StockRepository;
import com.lynx.simulation.repository.PriceHistoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
    private final AutoEventTrigger autoEventTrigger;
    private final MarketState marketState;
    private final SimulatedClock simulatedClock;

    @Value("${simulation.market-open-hour:9}")
    private int marketOpenHour;

    @Value("${simulation.market-close-hour:17}")
    private int marketCloseHour;

    @Value("${simulation.start-date:2024-01-15}")
    private String startDateStr;

    private final Map<String, Stock> stockCache = new LinkedHashMap<>();
    private int currentTick = 0;

    @PostConstruct
    public void init() {
        reloadCache();
        int tradingMinutesPerDay = (marketCloseHour - marketOpenHour) * 60;
        simulatedClock.seed(LocalDate.parse(startDateStr), marketOpenHour, currentTick, tradingMinutesPerDay);
        marketState.setOpen(true);
        int minuteOfDay = simulatedClock.getMinuteOfDay();
        log.info("Market opened on startup. Simulated time: {}:{}", minuteOfDay / 60,
                String.format("%02d", minuteOfDay % 60));
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
            // Ensure high/low are valid bounds around the restored current price
            if (stock.getHighPrice() < stock.getCurrentPrice()) stock.setHighPrice(stock.getCurrentPrice());
            if (stock.getLowPrice() == 0.0 || stock.getLowPrice() > stock.getCurrentPrice()) stock.setLowPrice(stock.getCurrentPrice());
            stockCache.put(stock.getTicker(), stock);
        });

        currentTick = (int) priceHistoryRepository.countByTicker(stocks.getFirst().getTicker());
        log.info("Restored tick counter to {}, {} stocks loaded", currentTick, stockCache.size());
    }

    @Scheduled(fixedRateString = "${simulation.tick-rate-ms}")
    public synchronized void tick() {
        simulatedClock.advance();
        int minuteOfDay = simulatedClock.getMinuteOfDay();

        if (minuteOfDay == marketOpenHour * 60 && !marketState.isOpen()) {
            log.info("Simulated time {}:00 — auto-opening market", marketOpenHour);
            openMarket();
        }

        if (!marketState.isOpen()) return;

        if (minuteOfDay == marketCloseHour * 60 && marketState.isOpen()) {
            log.info("Simulated time {}:00 — auto-closing market", marketCloseHour);
            closeMarket();
            return;
        }

        // Process event expirations and random triggers before price calculation so
        // the event state is consistent for all stocks in this tick
        autoEventTrigger.onTick();

        currentTick++;
        log.info("Tick #{} | prices: {}", currentTick,
                stockCache.values().stream()
                        .map(s -> s.getTicker() + "=" + s.getCurrentPrice())
                        .toList());

        stockCache.values().forEach(stock -> {
            var updatedPrice = priceCalculator.calculate(stock, currentTick);
            orderMatchingEngine.matchOrders(stock);
            priceHistoryRepository.save(updatedPrice);
            priceUpdateProducer.send(stock);
        });
    }

    public synchronized void openMarket() {
        marketState.setOpen(true);
        log.info("Market opened");
    }

    public synchronized void closeMarket() {
        marketState.setOpen(false);

        orderMatchingEngine.expireOpenOrders();

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
