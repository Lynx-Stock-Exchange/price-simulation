package com.lynx.simulation.events;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderPressureTracker {

    private final StringRedisTemplate redisTemplate;

    private static final String PRESSURE_KEY_PREFIX = "pressure:";
    private static final String BUY_FIELD = "BUY";
    private static final String SELL_FIELD = "SELL";

    /**
     * Called asynchronously by your Kafka Consumer the millisecond an order arrives.
     * Uses atomic increments to absolutely guarantee no race conditions.
     */
    public void recordOrderVolume(String ticker, String side, long quantity) {
        String hashKey = PRESSURE_KEY_PREFIX + ticker;
        redisTemplate.opsForHash().increment(hashKey, side.toUpperCase(), quantity);
    }

    /**
     * Called synchronously by the TickScheduler every 1 second.
     * Extracts the volume for the formula, then deletes the key to reset the pressure.
     */
    public PressureData getAndResetPressure(String ticker) {
        String hashKey = PRESSURE_KEY_PREFIX + ticker;

        Map<Object, Object> rawData = redisTemplate.opsForHash().entries(hashKey);

        redisTemplate.delete(hashKey);

        if (rawData.isEmpty()) {
            return new PressureData(0, 0);
        }

        long buyVol = parseVolume(rawData.get(BUY_FIELD));
        long sellVol = parseVolume(rawData.get(SELL_FIELD));

        return new PressureData(buyVol, sellVol);
    }

    private long parseVolume(Object value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // A clean, immutable record to pass the data back to the core math loop
    public record PressureData(long buyVolume, long sellVolume) {
        public long getTotalVolume() {
            return buyVolume + sellVolume;
        }
    }
}