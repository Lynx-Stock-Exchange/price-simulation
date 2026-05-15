package com.lynx.simulation.tick;

import com.lynx.simulation.events.AutoEventTrigger;
import com.lynx.simulation.events.MarketState;
import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.kafka.producers.MarketStatusProducer;
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
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class TickScheduler {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceCalculator priceCalculator;
    private final PriceUpdateProducer priceUpdateProducer;
    private final MarketStatusProducer marketStatusProducer;
    private final OrderMatchingEngine orderMatchingEngine;
    private final AutoEventTrigger autoEventTrigger;
    private final MarketState marketState;
    private final SimulatedClock simulatedClock;
    private final com.lynx.simulation.repository.OptionRepository optionRepository;
    private final com.lynx.simulation.kafka.producers.OptionProducer optionProducer;

    @Value("${simulation.market-open-hour:9}")
    private int marketOpenHour;

    @Value("${simulation.market-close-hour:17}")
    private int marketCloseHour;

    @Value("${simulation.start-date:2024-01-15}")
    private String startDateStr;

    private final Map<String, Stock> stockCache = new LinkedHashMap<>();
    private int currentTick = 0;
    private final AtomicInteger speedMultiplier = new AtomicInteger(1);
    // False when admin manually closed the market; prevents auto-open until admin explicitly reopens.
    private volatile boolean autoOpenEnabled = true;
    // True after today's auto-close has fired; reset when the clock re-enters the pre-close zone.
    private volatile boolean dailyCloseApplied = false;

    public void setSpeedMultiplier(int multiplier) {
        if (multiplier < 1) {
            log.warn("Invalid speed multiplier {}, ignoring", multiplier);
            return;
        }
        if (marketState.isOpen()) {
            log.warn("SET_SPEED rejected: close the market before changing simulation speed to avoid clock jumps");
            return;
        }
        speedMultiplier.set(multiplier);
        log.warn("⚡ ADMIN COMMAND: Simulation speed set to {}x", multiplier);
    }

    public synchronized void adminOpenMarket() {
        autoOpenEnabled = true;
        openMarket();
        log.warn("🟢 ADMIN COMMAND: Market is OPEN. Auto-scheduling re-enabled.");
    }

    public synchronized void adminCloseMarket() {
        autoOpenEnabled = false;
        closeMarket();
        log.warn("🔴 ADMIN COMMAND: Market is CLOSED. Auto-open disabled until manual reopen.");
    }

    @PostConstruct
    public void init() {
        reloadCache();
        int tradingMinutesPerDay = (marketCloseHour - marketOpenHour) * 60;
        simulatedClock.seed(LocalDate.parse(startDateStr), marketOpenHour, currentTick, tradingMinutesPerDay);
        openMarket();
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

        currentTick = (int) priceHistoryRepository.countByTicker(stocks.get(0).getTicker());
        log.info("Restored tick counter to {}, {} stocks loaded", currentTick, stockCache.size());
    }

    @Scheduled(fixedRateString = "${simulation.tick-rate-ms}")
    public synchronized void tick() {
        simulatedClock.advance(speedMultiplier.get());
        int minuteOfDay = simulatedClock.getMinuteOfDay();

        // Clock has re-entered the pre-close window — a new day is in progress.
        if (minuteOfDay < marketCloseHour * 60) {
            dailyCloseApplied = false;
        }

        if (!marketState.isOpen() && autoOpenEnabled && minuteOfDay >= marketOpenHour * 60 && minuteOfDay < marketCloseHour * 60) {
            log.info("Simulated time {}:{} — auto-opening market", minuteOfDay / 60, String.format("%02d", minuteOfDay % 60));
            openMarket();
        }

        if (!marketState.isOpen()) return;

        if (!dailyCloseApplied && minuteOfDay >= marketCloseHour * 60) {
            dailyCloseApplied = true;
            log.info("Simulated time {}:{} — auto-closing market", minuteOfDay / 60, String.format("%02d", minuteOfDay % 60));
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
        marketStatusProducer.sendStatusChange(true);
        optionRepository.findAll().forEach(optionProducer::send);
        log.info("Market opened, published {} options to Kafka", optionRepository.count());
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

        marketStatusProducer.sendStatusChange(false);
        log.info("Daily OHLC reset complete, market closed");
    }
}
