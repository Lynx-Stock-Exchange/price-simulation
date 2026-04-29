package com.lynx.simulation.model;

import java.time.Instant;

public record ActiveMarketEvent(
        String eventId,
        String eventType,   // BULL_RUN, BEAR_CRASH, SECTOR_BOOM, SECTOR_SLUMP, STOCK_SHOCK
        String scope,       // MARKET, SECTOR, or STOCK
        String target,      // null for MARKET scope, sector name for SECTOR scope, ticker for STOCK scope
        String headline,
        double magnitude,   // amplifier, e.g. 1.5 = 50% stronger moves
        int durationTicks,
        Instant triggeredAt,
        String triggeredBy  // SYSTEM or ADMIN
) {}
