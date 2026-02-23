package com.bank.app.batch.processor;

import com.bank.app.batch.model.FailedTransactionCleanupItem;
import com.bank.app.entity.Transaction;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FailedTransactionCleanupItemProcessor implements ItemProcessor<Transaction, FailedTransactionCleanupItem> {

    @Override
    public FailedTransactionCleanupItem process(Transaction transaction) {
        if (transaction.getTransactionDate() == null || transaction.getTransactionDate().isAfter(LocalDateTime.now().minusHours(24))) {
            return null;
        }

        transaction.setStatus(Transaction.TransactionStatus.FAILED);
        boolean compensationRequired = transaction.getSourceAccount() != null
                && (transaction.getType() == Transaction.TransactionType.TRANSFER
                || transaction.getType().isWithdrawal()
                || transaction.getType().isPayment());

        return FailedTransactionCleanupItem.builder()
                .transaction(transaction)
                .compensationRequired(compensationRequired)
                .build();
    }
}
