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
public class TransferV2Response {
    private String transferId;
    private String state;
    private BigDecimal amount;
    private String currency;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private LocalDateTime processedAt;
}
