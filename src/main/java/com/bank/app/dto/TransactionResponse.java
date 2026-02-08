package com.bank.app.dto;

import com.bank.app.entity.Transaction.TransactionStatus;
import com.bank.app.entity.Transaction.TransactionType;
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
public class TransactionResponse {
    private Long id;
    private String transactionReference;
    private TransactionType type;
    private BigDecimal amount;
    private String description;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private TransactionStatus status;
    private LocalDateTime transactionDate;
}
