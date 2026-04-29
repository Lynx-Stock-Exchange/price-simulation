package com.lynx.simulation.repository;

import com.lynx.simulation.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    Optional<PriceHistory> findTopByTickerOrderByTimestampDesc(String ticker);

    long countByTicker(String ticker);
}