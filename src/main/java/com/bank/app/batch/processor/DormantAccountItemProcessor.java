package com.bank.app.batch.processor;

import com.bank.app.entity.Account;
import com.bank.app.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DormantAccountItemProcessor implements ItemProcessor<Account, Account> {

    private final TransactionRepository transactionRepository;

    @Override
    public Account process(Account account) {
        long count = transactionRepository.countTransactionsForAccountSince(account.getId(), LocalDateTime.now().minusDays(90));
        return count == 0 ? account : null;
    }
}
