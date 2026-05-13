package com.lynx.simulation.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketStateTest {

    private final MarketState marketState = new MarketState();

    @Test
    void isOpen_defaultsFalse() {
        assertThat(marketState.isOpen()).isFalse();
    }

    @Test
    void setOpen_true_isOpenReturnsTrue() {
        marketState.setOpen(true);
        assertThat(marketState.isOpen()).isTrue();
    }

    @Test
    void setOpen_false_isOpenReturnsFalse() {
        marketState.setOpen(true);
        marketState.setOpen(false);
        assertThat(marketState.isOpen()).isFalse();
    }
}
