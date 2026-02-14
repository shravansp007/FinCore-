package com.bank.app.config;

import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpeningBalanceBackfill implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Value("${app.transaction.backfill-opening-balance:true}")
    private boolean backfillEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!backfillEnabled) {
            return;
        }

        List<Account> accounts = accountRepository.findAll();
        int created = 0;

        for (Account account : accounts) {
            BigDecimal balance = account.getBalance();
            if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            long txCount = transactionRepository.countBySourceAccountIdOrDestinationAccountId(account.getId(), account.getId());
            if (txCount > 0) {
                continue;
            }

            Transaction openingTx = Transaction.builder()
                    .transactionReference("OPEN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                    .type(Transaction.TransactionType.DEPOSIT)
                    .amount(balance)
                    .description("Opening balance (backfilled)")
                    .destinationAccount(account)
                    .status(Transaction.TransactionStatus.COMPLETED)
                    .transactionDate(account.getCreatedAt() != null ? account.getCreatedAt() : LocalDateTime.now())
                    .user(account.getUser())
                    .build();

            transactionRepository.save(openingTx);
            created++;
        }

        if (created > 0) {
            log.info("action=transaction.backfill.opening_balance created={}", created);
        }
    }
}
