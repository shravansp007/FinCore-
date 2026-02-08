package com.bank.app.dto;

import com.bank.app.entity.Account.AccountType;
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
public class AccountDTO {
    private Long id;
    private String accountNumber;
    private String maskedAccountNumber;
    private AccountType accountType;
    private BigDecimal balance;
    private String currency;
    private Boolean active;
    private LocalDateTime createdAt;
}
