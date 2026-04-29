package com.lynx.simulation.events;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class SimulatedClock {

    private LocalDateTime currentTime = LocalDateTime.now();
    private int minuteOfDay = 0;

    public synchronized void seed(LocalDate startDate, int marketOpenHour, int currentTick, int tradingMinutesPerDay) {
        if (tradingMinutesPerDay <= 0) {
            minuteOfDay = marketOpenHour * 60;
            currentTime = startDate.atTime(marketOpenHour, 0);
            return;
        }
        int completedDays = currentTick / tradingMinutesPerDay;
        int tickInDay = currentTick % tradingMinutesPerDay;
        minuteOfDay = marketOpenHour * 60 + tickInDay;
        currentTime = startDate.plusDays(completedDays).atStartOfDay().plusMinutes(minuteOfDay);
    }

    public synchronized void advance() {
        minuteOfDay = (minuteOfDay + 1) % 1440;
        currentTime = currentTime.plusMinutes(1);
    }

    public synchronized int getMinuteOfDay() {
        return minuteOfDay;
    }

    public synchronized String getFormattedTime() {
        return currentTime.toString();
    }

    public synchronized Instant getSimulatedInstant() {
        return currentTime.toInstant(ZoneOffset.UTC);
    }
}
