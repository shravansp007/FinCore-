package com.bank.app.service;

import com.bank.app.dto.AccountDTO;
import com.bank.app.dto.BeneficiaryDTO;
import com.bank.app.dto.DashboardResponse;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.Transaction.TransactionStatus;
import com.bank.app.entity.Transaction.TransactionType;
import com.bank.app.entity.User;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final BeneficiaryService beneficiaryService;
    private final TransactionRepository transactionRepository;

    public DashboardResponse getDashboard(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<AccountDTO> accounts = accountService.getUserAccounts(userEmail);
        BigDecimal totalBalance = accounts.stream()
                .filter(AccountDTO::getActive)
                .map(AccountDTO::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BeneficiaryDTO> beneficiaries = beneficiaryService.getMyBeneficiaries(userEmail);

        List<Transaction> userTransactions = transactionRepository.findByUserAccounts(user.getId());
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        BigDecimal monthlyCredit = BigDecimal.ZERO;
        BigDecimal monthlyDebit = BigDecimal.ZERO;

        for (Transaction tx : userTransactions) {
            if (tx.getTransactionDate() == null) {
                continue;
            }
            if (tx.getStatus() != TransactionStatus.COMPLETED) {
                continue;
            }
            if (tx.getTransactionDate().isBefore(monthStart) || tx.getTransactionDate().isAfter(monthEnd)) {
                continue;
            }

            boolean isCredit = isCreditTransaction(tx, user.getId());
            if (isCredit) {
                monthlyCredit = monthlyCredit.add(tx.getAmount());
            } else {
                monthlyDebit = monthlyDebit.add(tx.getAmount());
            }
        }

        List<TransactionResponse> recentTransactions = userTransactions.stream()
                .sorted((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()))
                .limit(5)
                .map(this::mapTransactionToResponse)
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .welcomeName(user.getFirstName())
                .totalBalance(totalBalance)
                .monthlyCredit(monthlyCredit)
                .monthlyDebit(monthlyDebit)
                .accountCount(accounts.size())
                .beneficiaryCount(beneficiaries.size())
                .accounts(accounts)
                .recentTransactions(recentTransactions)
                .build();
    }

    private boolean isCreditTransaction(Transaction tx, Long userId) {
        if (tx.getType() == TransactionType.DEPOSIT) {
            return tx.getDestinationAccount() != null && tx.getDestinationAccount().getUser().getId().equals(userId);
        }
        if (tx.getType() == TransactionType.TRANSFER) {
            return tx.getDestinationAccount() != null && tx.getDestinationAccount().getUser().getId().equals(userId);
        }
        return false;
    }

    private TransactionResponse mapTransactionToResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionReference(t.getTransactionReference())
                .type(t.getType())
                .amount(t.getAmount())
                .description(t.getDescription())
                .sourceAccountNumber(t.getSourceAccount() != null ? t.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(t.getDestinationAccount() != null ? t.getDestinationAccount().getAccountNumber() : null)
                .status(t.getStatus())
                .transactionDate(t.getTransactionDate())
                .build();
    }
}
