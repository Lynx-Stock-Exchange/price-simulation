package com.lynx.simulation.controller;

import com.lynx.simulation.model.Stock;
import com.lynx.simulation.repository.StockRepository;
import com.lynx.simulation.tick.TickScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockRepository stockRepository;
    private final TickScheduler tickScheduler;

    @PostMapping("/stocks/seed")
    public List<Map<String, Object>> seedStocks(@RequestBody List<Map<String, Object>> stocks) {
        for (Map<String, Object> s : stocks) {
            String ticker = stringVal(s, "ticker", null);
            if (ticker == null) continue;
            double startPrice = toDouble(s.get("start_price"));
            Stock stock = Stock.builder()
                    .ticker(ticker)
                    .name(stringVal(s, "name", ticker))
                    .sector(stringVal(s, "sector", "Unknown"))
                    .currentPrice(startPrice)
                    .openPrice(startPrice)
                    .highPrice(startPrice)
                    .lowPrice(startPrice)
                    .volume(0L)
                    .volatility(toDouble(s.get("volatility")))
                    .trendBias(toDouble(s.get("trend_bias")))
                    .eventWeight(toDouble(s.get("event_weight")))
                    .momentum(toDouble(s.get("momentum")))
                    .listedAt(Instant.now())
                    .build();
            stockRepository.save(stock);
            log.info("Seeded stock: {}", ticker);
        }
        tickScheduler.reloadCache();
        log.info("Reloaded tick cache after seeding {} stocks", stocks.size());
        return stocks;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(val));
    }

    private String stringVal(Map<String, Object> map, String key, String def) {
        Object v = map == null ? null : map.get(key);
        return v != null ? String.valueOf(v) : def;
    }
}
