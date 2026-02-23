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
public class IdempotentTransferResponse {
    private String status;
    private Long transactionId;
    private BigDecimal amount;
    private LocalDateTime timestamp;
}
