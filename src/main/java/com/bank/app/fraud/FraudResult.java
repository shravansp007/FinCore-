package com.bank.app.fraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudResult {
    private boolean flag;
    private String ruleName;
    private int riskScore;
    private String reason;
}
