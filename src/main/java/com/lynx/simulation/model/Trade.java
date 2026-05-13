package com.lynx.simulation.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    private String tradeId;

    private String orderId;
    private String platformId;
    private String platformUserId;

    private String instrumentType;
    private String instrumentId;

    @Enumerated(EnumType.STRING)
    private Order.Side side;

    private int quantity;
    private double price;
    private double exchangeFee;

    private Instant executedAt;
}
