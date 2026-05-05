package com.lynx.simulation.tick;

import com.lynx.simulation.events.AutoEventTrigger;
import com.lynx.simulation.events.OrderPressureTracker;
import com.lynx.simulation.events.SimulatedClock;
import com.lynx.simulation.model.PriceHistory;
import com.lynx.simulation.model.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceCalculatorTest {

    @Mock private OrderPressureTracker pressureTracker;
    @Mock private AutoEventTrigger autoEventTrigger;
    @Mock private SimulatedClock simulatedClock;
    @InjectMocks private PriceCalculator calculator;

    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-15T09:00:00Z");

    @BeforeEach
    void setUp() {
        when(simulatedClock.getSimulatedInstant()).thenReturn(FIXED_INSTANT);
        when(pressureTracker.getAndResetPressure(any())).thenReturn(new OrderPressureTracker.PressureData(0, 0));
        when(autoEventTrigger.getActiveMagnitudeFor(any())).thenReturn(1.0);
    }

    // volatility=0 eliminates the random component, making results deterministic

    private Stock stock(double price, double trendBias, double volatility, double eventWeight, double momentum) {
        return Stock.builder()
                .ticker("ARKA").sector("TECH")
                .currentPrice(price).openPrice(price)
                .highPrice(price).lowPrice(price).volume(0L)
                .trendBias(trendBias).volatility(volatility)
                .eventWeight(eventWeight).momentum(momentum)
                .build();
    }

    @Test
    void calculate_withZeroVolatility_driftsByTrendBiasOnly() {
        Stock stock = stock(100.0, 5.0, 0.0, 1.0, 0.0);

        PriceHistory result = calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isEqualTo(105.0);
        assertThat(result.getOpen()).isEqualTo(100.0);
        assertThat(result.getClose()).isEqualTo(105.0);
    }

    @Test
    void calculate_withNegativeTrendBias_priceDeclinesBy() {
        Stock stock = stock(100.0, -5.0, 0.0, 1.0, 0.0);

        calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isEqualTo(95.0);
    }

    @Test
    void calculate_withActiveEvent_eventComponentAmplifies() {
        // randomWalk = 0 + 10.0 = 10.0; eventComponent = 10.0 * (2.0-1.0) * 1.5 = 15.0
        // newPrice = 100 + 10 + 0 + 15 = 125
        Stock stock = stock(100.0, 10.0, 0.0, 1.5, 0.0);
        when(autoEventTrigger.getActiveMagnitudeFor(stock)).thenReturn(2.0);

        calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isEqualTo(125.0);
    }

    @Test
    void calculate_noActiveEvent_eventComponentIsZero() {
        // magnitude = 1.0 → eventComponent = 0 regardless of trendBias
        Stock stock = stock(100.0, 5.0, 0.0, 2.0, 0.0);
        // autoEventTrigger already stubbed to return 1.0 in setUp

        calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isEqualTo(105.0); // just trendBias, no event amplification
    }

    @Test
    void calculate_buyHeavyPressure_clampedToFivePercent() {
        // pressureRatio = min(0.05, (100-0)/100) = 0.05; orderPressure = 0.05 * 100 * 0.5 = 2.5
        Stock stock = stock(100.0, 0.0, 0.0, 1.0, 0.5);
        when(pressureTracker.getAndResetPressure("ARKA"))
                .thenReturn(new OrderPressureTracker.PressureData(100, 0));

        calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isEqualTo(102.5);
    }

    @Test
    void calculate_sellHeavyPressure_clampedToMinusFivePercent() {
        // pressureRatio = max(-0.05, (0-100)/100) = -0.05; orderPressure = -0.05 * 100 * 0.5 = -2.5
        Stock stock = stock(100.0, 0.0, 0.0, 1.0, 0.5);
        when(pressureTracker.getAndResetPressure("ARKA"))
                .thenReturn(new OrderPressureTracker.PressureData(0, 100));

        calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isEqualTo(97.5);
    }

    @Test
    void calculate_priceNeverFallsBelowFloor() {
        Stock stock = stock(0.01, -1000.0, 0.0, 1.0, 0.0);

        calculator.calculate(stock, 1);

        assertThat(stock.getCurrentPrice()).isGreaterThanOrEqualTo(0.01);
    }

    @Test
    void calculate_priceRoundedToTwoDecimalPlaces() {
        // trendBias = 1/3 causes rounding to kick in
        Stock stock = stock(100.0, 1.0 / 3.0, 0.0, 1.0, 0.0);

        PriceHistory result = calculator.calculate(stock, 1);

        double price = result.getClose();
        assertThat(Math.round(price * 100.0) / 100.0).isEqualTo(price);
    }

    @Test
    void calculate_accumulatesVolumeOnStock() {
        Stock stock = stock(100.0, 0.0, 0.0, 1.0, 0.5);
        when(pressureTracker.getAndResetPressure("ARKA"))
                .thenReturn(new OrderPressureTracker.PressureData(60, 40));

        calculator.calculate(stock, 1);

        assertThat(stock.getVolume()).isEqualTo(100L);
    }

    @Test
    void calculate_updatesStockHighAndLow() {
        Stock stock = stock(100.0, 10.0, 0.0, 1.0, 0.0);

        calculator.calculate(stock, 1);

        assertThat(stock.getHighPrice()).isEqualTo(110.0);
        assertThat(stock.getLowPrice()).isEqualTo(100.0);
    }

    @Test
    void calculate_priceHistoryOhlcReflectsTick() {
        Stock stock = stock(100.0, 5.0, 0.0, 1.0, 0.0);

        PriceHistory result = calculator.calculate(stock, 1);

        assertThat(result.getOpen()).isEqualTo(100.0);
        assertThat(result.getClose()).isEqualTo(105.0);
        assertThat(result.getHigh()).isEqualTo(105.0);
        assertThat(result.getLow()).isEqualTo(100.0);
    }

    @Test
    void calculate_priceHistoryTimestampUsesSimulatedClock() {
        Stock stock = stock(100.0, 0.0, 0.0, 1.0, 0.0);

        PriceHistory result = calculator.calculate(stock, 1);

        assertThat(result.getTimestamp()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void calculate_perTickVolumeInHistory_notCumulativeStockVolume() {
        Stock stock = stock(100.0, 0.0, 0.0, 1.0, 0.5);
        stock.setVolume(500L); // pre-existing daily volume
        when(pressureTracker.getAndResetPressure("ARKA"))
                .thenReturn(new OrderPressureTracker.PressureData(30, 10));

        PriceHistory result = calculator.calculate(stock, 1);

        assertThat(result.getVolume()).isEqualTo(40L);     // tick volume only
        assertThat(stock.getVolume()).isEqualTo(540L);     // cumulative daily
    }
}
