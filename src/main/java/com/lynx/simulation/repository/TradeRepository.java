package com.lynx.simulation.repository;

import com.lynx.simulation.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, String> {
}
