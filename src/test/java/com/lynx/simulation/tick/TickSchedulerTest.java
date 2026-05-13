package com.lynx.simulation.tick;

import com.lynx.simulation.events.AutoEventTrigger;
import com.lynx.simulation.events.MarketState;
import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.kafka.producers.PriceUpdateProducer;
import com.lynx.simulation.model.PriceHistory;
import com.lynx.simulation.model.Stock;
import com.lynx.simulation.repository.PriceHistoryRepository;
import com.lynx.simulation.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TickSchedulerTest {

    @Mock private StockRepository stockRepository;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private PriceCalculator priceCalculator;
    @Mock private PriceUpdateProducer priceUpdateProducer;
    @Mock private OrderMatchingEngine orderMatchingEngine;
    @Mock private AutoEventTrigger autoEventTrigger;
    @Mock private MarketState marketState;
    @Mock private SimulatedClock simulatedClock;
    @Mock private PriceHistory priceHistory;
    @InjectMocks private TickScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "marketOpenHour", 9);
        ReflectionTestUtils.setField(scheduler, "marketCloseHour", 17);
        ReflectionTestUtils.setField(scheduler, "startDateStr", "2024-01-15");
        when(priceCalculator.calculate(any(), anyInt())).thenReturn(priceHistory);
    }

    @SuppressWarnings("unchecked")
    private void addStockToCache(Stock stock) {
        ((Map<String, Stock>) ReflectionTestUtils.getField(scheduler, "stockCache"))
                .put(stock.getTicker(), stock);
    }

    // --- auto-close at 17:00 ---

    @Test
    void tick_atMarketCloseTime_closesMarketAndExpiresOrders() {
        when(simulatedClock.getMinuteOfDay()).thenReturn(17 * 60);
        when(marketState.isOpen()).thenReturn(true);

        scheduler.tick();

        verify(marketState).setOpen(false);
        verify(orderMatchingEngine).expireOpenOrders();
    }

    @Test
    void tick_atCloseTime_returnsWithoutProcessingPrices() {
        when(simulatedClock.getMinuteOfDay()).thenReturn(17 * 60);
        when(marketState.isOpen()).thenReturn(true);
        addStockToCache(Stock.builder().ticker("ARKA").currentPrice(100.0).sector("TECH").build());

        scheduler.tick();

        verify(priceCalculator, never()).calculate(any(), anyInt());
    }

    // --- auto-open at 09:00 ---

    @Test
    void tick_atMarketOpenTime_opensMarket() {
        when(simulatedClock.getMinuteOfDay()).thenReturn(9 * 60);
        when(marketState.isOpen()).thenReturn(false);

        scheduler.tick();

        verify(marketState).setOpen(true);
    }

    // --- market closed mid-day ---

    @Test
    void tick_whenMarketClosed_doesNotProcessPrices() {
        when(simulatedClock.getMinuteOfDay()).thenReturn(600); // 10:00, not open/close boundary
        when(marketState.isOpen()).thenReturn(false);
        addStockToCache(Stock.builder().ticker("ARKA").currentPrice(100.0).sector("TECH").build());

        scheduler.tick();

        verify(priceCalculator, never()).calculate(any(), anyInt());
    }

    // --- normal tick ---

    @Test
    void tick_whenMarketOpen_processesEachStock() {
        Stock arka = Stock.builder().ticker("ARKA").currentPrice(100.0).openPrice(100.0).sector("TECH").build();
        addStockToCache(arka);

        when(simulatedClock.getMinuteOfDay()).thenReturn(600); // 10:00
        when(marketState.isOpen()).thenReturn(true);

        scheduler.tick();

        verify(priceCalculator).calculate(eq(arka), anyInt());
        verify(orderMatchingEngine).matchOrders(arka);
        verify(priceHistoryRepository).save(priceHistory);
        verify(priceUpdateProducer).send(arka);
    }

    @Test
    void tick_whenMarketOpen_callsAutoEventTrigger() {
        when(simulatedClock.getMinuteOfDay()).thenReturn(600);
        when(marketState.isOpen()).thenReturn(true);

        scheduler.tick();

        verify(autoEventTrigger).onTick();
    }

    // --- multi-stock orchestration ---

    @Test
    void tick_withMultipleStocksInCache_processesAll() {
        Stock arka = Stock.builder().ticker("ARKA").currentPrice(100.0).openPrice(100.0).sector("TECH").build();
        Stock mnvs = Stock.builder().ticker("MNVS").currentPrice(50.0).openPrice(50.0).sector("FINANCE").build();
        addStockToCache(arka);
        addStockToCache(mnvs);

        when(simulatedClock.getMinuteOfDay()).thenReturn(600);
        when(marketState.isOpen()).thenReturn(true);

        scheduler.tick();

        verify(priceCalculator).calculate(eq(arka), anyInt());
        verify(priceCalculator).calculate(eq(mnvs), anyInt());
        verify(orderMatchingEngine).matchOrders(arka);
        verify(orderMatchingEngine).matchOrders(mnvs);
    }

    // --- double-close guard ---

    @Test
    void tick_whenAlreadyClosed_autoCloseDoesNotCallExpireAgain() {
        // Market was already closed by an admin before 17:00 auto-close fires
        when(simulatedClock.getMinuteOfDay()).thenReturn(17 * 60);
        when(marketState.isOpen()).thenReturn(false); // already closed

        scheduler.tick();

        verify(orderMatchingEngine, never()).expireOpenOrders();
    }

    // --- OHLC reset on close ---

    @Test
    void closeMarket_resetsOhlcForEachStock() {
        Stock arka = Stock.builder()
                .ticker("ARKA").currentPrice(120.0).openPrice(100.0)
                .highPrice(125.0).lowPrice(98.0).volume(500L).sector("TECH")
                .build();
        addStockToCache(arka);

        scheduler.closeMarket();

        assertThat(arka.getOpenPrice()).isEqualTo(120.0);
        assertThat(arka.getHighPrice()).isEqualTo(120.0);
        assertThat(arka.getLowPrice()).isEqualTo(120.0);
        assertThat(arka.getVolume()).isEqualTo(0L);
    }
}
