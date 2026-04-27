package com.lynx.simulation.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    private String orderId;

    private String platformId;
    private String platformUserId;

    private String instrumentType;
    private String instrumentId;
    private String orderType;

    public enum Side {
        BUY, SELL
    }

    @Enumerated(EnumType.STRING)
    private Side side;

    private int quantity;
    private int filledQuantity;

    private Double limitPrice;
    private Double averageFillPrice;
    private Double exchangeFee;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
}