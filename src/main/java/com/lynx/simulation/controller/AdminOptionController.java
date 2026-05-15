package com.lynx.simulation.controller;

import com.lynx.simulation.kafka.producers.OptionProducer;
import com.lynx.simulation.model.OptionContract;
import com.lynx.simulation.repository.OptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOptionController {

    private final OptionRepository optionRepository;
    private final OptionProducer optionProducer;

    @PostMapping("/options")
    public Map<String, Object> saveOption(@RequestBody Map<String, Object> payload) {
        String optionId = (String) payload.get("option_id");
        if (optionId == null) return payload;

        OptionContract option = OptionContract.builder()
                .optionId(optionId)
                .underlyingTicker(stringVal(payload, "underlying_ticker", "ARKA"))
                .optionType(stringVal(payload, "option_type", "CALL"))
                .strikePrice(toDouble(payload.get("strike_price")))
                .expiryTime(stringVal(payload, "expiry_time", ""))
                .premium(toDouble(payload.get("premium")))
                .active(Boolean.TRUE.equals(payload.get("is_active")))
                .autoExercise(Boolean.TRUE.equals(payload.get("auto_exercise")))
                .build();

        optionRepository.save(option);
        log.info("Saved option: {}", optionId);
        return payload;
    }

    private String stringVal(Map<String, Object> map, String key, String def) {
        Object v = map == null ? null : map.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(val));
    }
}
