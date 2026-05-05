package com.lynx.simulation.events;

import com.lynx.simulation.config.EventDefinitionConfig;
import com.lynx.simulation.model.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoEventTriggerTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private EventDefinitionConfig eventConfig;
    @Mock private MarketState marketState;
    @Mock private SimulatedClock simulatedClock;
    @InjectMocks private AutoEventTrigger trigger;

    @BeforeEach
    void setUp() {
        when(simulatedClock.getFormattedTime()).thenReturn("2024-01-15T09:00");
    }

    private Stock stock(String ticker, String sector) {
        return Stock.builder().ticker(ticker).sector(sector).build();
    }

    private EventDefinitionConfig.EventDefinition def(
            String type, String scope, String target, double magnitude, int duration) {
        EventDefinitionConfig.EventDefinition d = new EventDefinitionConfig.EventDefinition();
        d.setEventType(type);
        d.setScope(scope);
        d.setTarget(target);
        d.setMagnitude(magnitude);
        d.setDurationTicks(duration);
        d.setWeight(1.0);
        d.setHeadlines(List.of("Test headline"));
        return d;
    }

    // --- getActiveMagnitudeFor ---

    @Test
    void getActiveMagnitudeFor_noActiveEvents_returnsOne() {
        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.0);
    }

    @Test
    void getActiveMagnitudeFor_marketEvent_returnsMagnitude() {
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 10)));
        trigger.triggerEventByType("BULL_RUN");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5);
    }

    @Test
    void getActiveMagnitudeFor_sectorEvent_matchingSector_returnsMagnitude() {
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", "TECH", 1.5, 10)));
        trigger.triggerEventByType("SECTOR_BOOM");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5);
    }

    @Test
    void getActiveMagnitudeFor_sectorEvent_differentSector_returnsOne() {
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", "TECH", 1.5, 10)));
        trigger.triggerEventByType("SECTOR_BOOM");

        assertThat(trigger.getActiveMagnitudeFor(stock("MNVS", "FINANCE"))).isEqualTo(1.0);
    }

    @Test
    void getActiveMagnitudeFor_stockEvent_affectsOnlyTargetTicker() {
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("STOCK_SHOCK", "STOCK", "ARKA", 2.0, 5)));
        trigger.triggerEventByType("STOCK_SHOCK");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(2.0);
        assertThat(trigger.getActiveMagnitudeFor(stock("MNVS", "TECH"))).isEqualTo(1.0);
    }

    @Test
    void getActiveMagnitudeFor_marketAndSectorBothActive_stacksMultiplicatively() {
        when(eventConfig.getDefinitions())
                .thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 10)))
                .thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", "TECH", 2.0, 10)));
        trigger.triggerEventByType("BULL_RUN");
        trigger.triggerEventByType("SECTOR_BOOM");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5 * 2.0);
    }

    // --- deduplication and null guard ---

    @Test
    void triggerEvent_secondMarketWideEvent_isDropped() {
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 10)));
        trigger.triggerEventByType("BULL_RUN");
        trigger.triggerEventByType("BULL_RUN"); // duplicate — must be dropped

        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    @Test
    void triggerEvent_nonMarketWithNullTarget_isDropped() {
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", null, 1.5, 10)));
        trigger.triggerEventByType("SECTOR_BOOM");

        verify(kafkaTemplate, never()).send(any(), any(), any());
        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.0);
    }

    // --- expiry ---

    @Test
    void onTick_eventWithDurationOne_expiredAfterOneTick() {
        when(marketState.isOpen()).thenReturn(true);
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 1)));
        trigger.triggerEventByType("BULL_RUN");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5);

        trigger.onTick(); // ticksRemaining 1 → 0 → event removed

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.0);
    }

    @Test
    void onTick_closedMarket_doesNotProcessEvents() {
        when(marketState.isOpen()).thenReturn(false);
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 1)));
        trigger.triggerEventByType("BULL_RUN");

        trigger.onTick(); // market closed — no expiry processing

        // event should still be active
        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5);
    }

    // --- multi-event stacking ---

    @Test
    void getActiveMagnitudeFor_sectorAndStockBothActive_stacksMultiplicatively() {
        when(eventConfig.getDefinitions())
                .thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", "TECH", 2.0, 10)))
                .thenReturn(List.of(def("STOCK_SHOCK", "STOCK", "ARKA", 1.5, 5)));
        trigger.triggerEventByType("SECTOR_BOOM");
        trigger.triggerEventByType("STOCK_SHOCK");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(2.0 * 1.5);
    }

    @Test
    void getActiveMagnitudeFor_allThreeScopesActive_stacksAll() {
        when(eventConfig.getDefinitions())
                .thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 10)))
                .thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", "TECH", 2.0, 10)))
                .thenReturn(List.of(def("STOCK_SHOCK", "STOCK", "ARKA", 1.2, 5)));
        trigger.triggerEventByType("BULL_RUN");
        trigger.triggerEventByType("SECTOR_BOOM");
        trigger.triggerEventByType("STOCK_SHOCK");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5 * 2.0 * 1.2);
    }

    // --- random event probability ---

    @Test
    void onTick_withZeroProbability_neverTriggersRandomEvent() {
        ReflectionTestUtils.setField(trigger, "eventProbability", -1.0); // nextDouble() is always >= 0
        when(marketState.isOpen()).thenReturn(true);

        for (int i = 0; i < 100; i++) trigger.onTick();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void onTick_withFullProbability_triggersRandomEvent() {
        ReflectionTestUtils.setField(trigger, "eventProbability", 1.0); // nextDouble() < 1.0 always
        when(marketState.isOpen()).thenReturn(true);
        when(eventConfig.getDefinitions()).thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 10)));

        trigger.onTick();

        verify(kafkaTemplate, times(1)).send(any(), any(), any());
    }

    // --- partial event expiry ---

    @Test
    void onTick_oneOfTwoEventsExpires_remainingEventStillApplied() {
        when(marketState.isOpen()).thenReturn(true);
        when(eventConfig.getDefinitions())
                .thenReturn(List.of(def("BULL_RUN", "MARKET", null, 1.5, 1)))   // expires after 1 tick
                .thenReturn(List.of(def("SECTOR_BOOM", "SECTOR", "TECH", 2.0, 10))); // lasts 10 ticks
        trigger.triggerEventByType("BULL_RUN");
        trigger.triggerEventByType("SECTOR_BOOM");

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(1.5 * 2.0);

        trigger.onTick(); // BULL_RUN expires; SECTOR_BOOM still has 9 ticks

        assertThat(trigger.getActiveMagnitudeFor(stock("ARKA", "TECH"))).isEqualTo(2.0);
        assertThat(trigger.getActiveMagnitudeFor(stock("MNVS", "FINANCE"))).isEqualTo(1.0);
    }
}
