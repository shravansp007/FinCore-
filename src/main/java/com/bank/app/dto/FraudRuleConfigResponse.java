package com.bank.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRuleConfigResponse {
    private String ruleName;
    private BigDecimal thresholdAmount;
    private LocalDateTime updatedAt;
}
