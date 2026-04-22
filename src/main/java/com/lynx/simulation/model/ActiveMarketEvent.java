package com.lynx.simulation.model;

public record ActiveMarketEvent(
        String eventId,
        String scope,     // MARKET, SECTOR, or STOCK
        String target,    // GLOBAL, TECH, ARKA, etc.
        String headline,
        double magnitude, // e.g., 1.05 for a 5% boost
        int durationTicks
) {}