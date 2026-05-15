package com.lynx.simulation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "option_contracts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionContract {

    @Id
    private String optionId;
    private String underlyingTicker;
    private String optionType;
    private double strikePrice;
    private String expiryTime;
    private double premium;
    @Column(name = "is_active")
    private boolean active;
    private boolean autoExercise;
}
