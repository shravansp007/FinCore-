package com.bank.app.batch.processor;

import com.bank.app.entity.Account;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class MonthlyStatementItemProcessor implements ItemProcessor<Account, Account> {
    @Override
    public Account process(Account item) {
        return item;
    }
}
