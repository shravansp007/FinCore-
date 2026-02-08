package com.bank.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private String welcomeName;
    private BigDecimal totalBalance;
    private BigDecimal monthlyCredit;
    private BigDecimal monthlyDebit;
    private int accountCount;
    private int beneficiaryCount;
    private List<AccountDTO> accounts;
    private List<TransactionResponse> recentTransactions;
}
