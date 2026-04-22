package com.lynx.simulation.repository;

import com.lynx.simulation.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    List<PriceHistory> findByTickerOrderByTimestampDesc(String ticker);

    Optional<PriceHistory> findTopByTickerOrderByTimestampDesc(String ticker);

    Object countByTickerAndTimestampAfter(String ticker, Instant listedAt);
}