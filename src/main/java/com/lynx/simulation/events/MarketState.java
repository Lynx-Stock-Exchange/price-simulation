package com.lynx.simulation.events;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class MarketState {
    private final AtomicBoolean isMarketOpen = new AtomicBoolean(false);

    public boolean isOpen() {
        return isMarketOpen.get();
    }

    public void setOpen(boolean state) {
        isMarketOpen.set(state);
    }
}