package com.bank.app.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private String eventType;
    private Long transactionId;
    private String transactionReference;
    private Long accountId;
    private String accountNumber;
    private BigDecimal amount;
    private String performedBy;
    private String accountHolderEmail;
    private String ipAddress;
    private LocalDateTime timestamp;
    private String traceId;
    private Map<String, Object> metadata;
}
