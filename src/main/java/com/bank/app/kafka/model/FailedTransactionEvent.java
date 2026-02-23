package com.bank.app.kafka.model;

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
public class FailedTransactionEvent {
    private Long transactionId;
    private String transactionReference;
    private Long accountId;
    private BigDecimal amount;
    private String reason;
    private boolean compensationCreated;
    private LocalDateTime timestamp;
}
