package com.bank.app.dto;

import com.bank.app.entity.FraudAlert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertResponse {
    private Long id;
    private Long transactionId;
    private Long accountId;
    private Integer riskScore;
    private String triggeredRules;
    private FraudAlert.FraudAlertStatus status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
