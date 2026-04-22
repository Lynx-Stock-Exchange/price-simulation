package com.lynx.simulation.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "stocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stock {

    @Id
    private String ticker;            // Max 5 chars, uppercase (e.g., "ARKA")

    private String name;              // Full company name
    private String sector;            // e.g., "Tech", "Finance"

    // --- Live Session Prices ---
    private double currentPrice;      // Decimal, 2dp
    private double openPrice;         // Price at market open for the current day
    private double highPrice;         // Day High
    private double lowPrice;          // Day Low

    // --- Volume Tracking ---
    private long volume;              // Total shares traded in current day

    // --- Core Simulation Parameters ---
    private double volatility;        // Price swing magnitude per tick (e.g., 0.03)
    private double trendBias;         // Drift per tick (positive/negative)
    private double eventWeight;       // Multiplier for market event impact
    private double momentum;          // Order pressure influence (0.0 - 1.0)

    // --- Metadata ---
    private Instant listedAt;         // Simulated market listing timestamp
}