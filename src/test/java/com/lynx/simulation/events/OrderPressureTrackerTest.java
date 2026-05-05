package com.lynx.simulation.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPressureTrackerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    @Mock private HashOperations<String, Object, Object> hashOps;
    @InjectMocks private OrderPressureTracker tracker;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
    }

    @Test
    void recordOrderVolume_incrementsCorrectHashField() {
        tracker.recordOrderVolume("ARKA", "BUY", 150);
        verify(hashOps).increment("pressure:ARKA", "BUY", 150);
    }

    @Test
    void recordOrderVolume_uppercasesSideBeforeStoring() {
        tracker.recordOrderVolume("ARKA", "sell", 50);
        verify(hashOps).increment("pressure:ARKA", "SELL", 50);
    }

    @Test
    void getAndResetPressure_returnsBuyAndSellVolumes() {
        Map<Object, Object> data = new HashMap<>();
        data.put("BUY", "300");
        data.put("SELL", "100");
        when(hashOps.entries("pressure:ARKA")).thenReturn(data);

        OrderPressureTracker.PressureData result = tracker.getAndResetPressure("ARKA");

        assertThat(result.buyVolume()).isEqualTo(300L);
        assertThat(result.sellVolume()).isEqualTo(100L);
        assertThat(result.getTotalVolume()).isEqualTo(400L);
    }

    @Test
    void getAndResetPressure_deletesRedisKeyAfterRead() {
        when(hashOps.entries("pressure:ARKA")).thenReturn(new HashMap<>());

        tracker.getAndResetPressure("ARKA");

        verify(redisTemplate).delete("pressure:ARKA");
    }

    @Test
    void getAndResetPressure_emptyHash_returnsZeroPressure() {
        when(hashOps.entries("pressure:ARKA")).thenReturn(new HashMap<>());

        OrderPressureTracker.PressureData result = tracker.getAndResetPressure("ARKA");

        assertThat(result.buyVolume()).isZero();
        assertThat(result.sellVolume()).isZero();
        assertThat(result.getTotalVolume()).isZero();
    }

    @Test
    void getAndResetPressure_missingSide_defaultsToZero() {
        Map<Object, Object> data = new HashMap<>();
        data.put("BUY", "200"); // no SELL entry
        when(hashOps.entries("pressure:ARKA")).thenReturn(data);

        OrderPressureTracker.PressureData result = tracker.getAndResetPressure("ARKA");

        assertThat(result.buyVolume()).isEqualTo(200L);
        assertThat(result.sellVolume()).isZero();
    }
}
