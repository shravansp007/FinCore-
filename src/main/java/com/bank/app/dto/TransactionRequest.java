package com.bank.app.dto;

import com.bank.app.entity.Transaction.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
    private BigDecimal amount;
    private TransactionType type; // DEPOSIT / WITHDRAW / TRANSFER
    private Long sourceAccountId;
    private Long destinationAccountId;
}
