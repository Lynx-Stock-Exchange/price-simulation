package com.lynx.simulation.repository;

import com.lynx.simulation.model.Order;
import com.lynx.simulation.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByInstrumentIdAndStatusIn(String ticker, List<OrderStatus> statuses);

    List<Order> findByStatusIn(List<OrderStatus> statuses);
}