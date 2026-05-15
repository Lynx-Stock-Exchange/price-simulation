package com.lynx.simulation.kafka.producers;

import com.lynx.simulation.model.OptionContract;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OptionProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(OptionContract option) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("option_id", option.getOptionId());
        payload.put("underlying_ticker", option.getUnderlyingTicker());
        payload.put("option_type", option.getOptionType());
        payload.put("strike_price", option.getStrikePrice());
        payload.put("expiry_time", option.getExpiryTime());
        payload.put("premium", option.getPremium());
        payload.put("is_active", option.isActive());
        payload.put("auto_exercise", option.isAutoExercise());
        kafkaTemplate.send("market.options", option.getOptionId(), payload);
    }
}
