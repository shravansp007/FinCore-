package com.bank.app.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DormantAccountEvent {
    private Long accountId;
    private String accountNumber;
    private String accountHolderEmail;
    private String status;
    private LocalDateTime timestamp;
}
