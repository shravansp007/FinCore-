package com.bank.app.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleConfigRequest {

    @NotNull(message = "thresholdAmount is required")
    @DecimalMin(value = "0.01", message = "thresholdAmount must be positive")
    private BigDecimal thresholdAmount;
}
