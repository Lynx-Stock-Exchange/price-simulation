package com.lynx.simulation.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatedClockTest {

    private final SimulatedClock clock = new SimulatedClock();

    @Test
    void seed_freshStart_setsMinuteOfDayToMarketOpen() {
        clock.seed(LocalDate.of(2024, 1, 15), 9, 0, 480);
        assertThat(clock.getMinuteOfDay()).isEqualTo(9 * 60);
    }

    @Test
    void seed_freshStart_setsTimeToMarketOpenOnStartDate() {
        clock.seed(LocalDate.of(2024, 1, 15), 9, 0, 480);
        assertThat(clock.getFormattedTime()).startsWith("2024-01-15T09:00");
    }

    @Test
    void seed_withOneDayOfTicks_advancesToNextDay() {
        clock.seed(LocalDate.of(2024, 1, 15), 9, 480, 480);
        assertThat(clock.getMinuteOfDay()).isEqualTo(9 * 60);
        assertThat(clock.getFormattedTime()).startsWith("2024-01-16T09:00");
    }

    @Test
    void seed_midDay_setsCorrectMinuteOfDay() {
        // tick 120 = 120 minutes into session → 11:00
        clock.seed(LocalDate.of(2024, 1, 15), 9, 120, 480);
        assertThat(clock.getMinuteOfDay()).isEqualTo(9 * 60 + 120);
        assertThat(clock.getFormattedTime()).startsWith("2024-01-15T11:00");
    }

    @Test
    void advance_incrementsMinuteOfDay() {
        clock.seed(LocalDate.of(2024, 1, 15), 9, 0, 480);
        clock.advance();
        assertThat(clock.getMinuteOfDay()).isEqualTo(9 * 60 + 1);
    }

    @Test
    void advance_wrapsAtMidnight() {
        // seed at 23:59, one advance should wrap to 0
        clock.seed(LocalDate.of(2024, 1, 15), 23, 59, 60);
        clock.advance();
        assertThat(clock.getMinuteOfDay()).isEqualTo(0);
    }

    @Test
    void getSimulatedInstant_matchesSeededTime() {
        clock.seed(LocalDate.of(2024, 1, 15), 9, 0, 480);
        Instant instant = clock.getSimulatedInstant();
        assertThat(instant.toString()).startsWith("2024-01-15T09:00:00");
    }

    @Test
    void getSimulatedInstant_isLaterAfterAdvance() {
        clock.seed(LocalDate.of(2024, 1, 15), 9, 0, 480);
        Instant before = clock.getSimulatedInstant();
        clock.advance();
        assertThat(clock.getSimulatedInstant()).isAfter(before);
    }

    @Test
    void seed_withMultipleDaysOfTicks_advancesToCorrectDayAndTime() {
        // 1000 ticks on a 480-tick day: 2 full days (960 ticks) + 40 ticks into day 3 = 09:40
        clock.seed(LocalDate.of(2024, 1, 15), 9, 1000, 480);
        assertThat(clock.getMinuteOfDay()).isEqualTo(9 * 60 + 40);
        assertThat(clock.getFormattedTime()).startsWith("2024-01-17T09:40");
    }
}
