package com.bank.app.batch.writer;

import com.bank.app.batch.model.FailedTransactionCleanupItem;
import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.kafka.FailedTransactionEventPublisher;
import com.bank.app.kafka.model.FailedTransactionEvent;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FailedTransactionCleanupItemWriter implements ItemWriter<FailedTransactionCleanupItem> {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final FailedTransactionEventPublisher failedTransactionEventPublisher;

    @Override
    public void write(Chunk<? extends FailedTransactionCleanupItem> chunk) {
        for (FailedTransactionCleanupItem item : chunk.getItems()) {
            Transaction transaction = item.getTransaction();
            transactionRepository.save(transaction);

            boolean compensationCreated = false;
            if (item.isCompensationRequired() && transaction.getSourceAccount() != null) {
                Account source = transaction.getSourceAccount();
                source.setBalance(source.getBalance().add(transaction.getAmount()));
                accountRepository.save(source);

                Transaction compensation = Transaction.builder()
                        .transactionReference(generateReference())
                        .type(Transaction.TransactionType.DEPOSIT)
                        .amount(transaction.getAmount())
                        .description("Compensation for failed transaction " + transaction.getTransactionReference())
                        .destinationAccount(source)
                        .status(Transaction.TransactionStatus.COMPLETED)
                        .user(source.getUser())
                        .build();
                transactionRepository.save(compensation);
                compensationCreated = true;
            }

            failedTransactionEventPublisher.publish(
                    FailedTransactionEvent.builder()
                            .transactionId(transaction.getId())
                            .transactionReference(transaction.getTransactionReference())
                            .accountId(transaction.getSourceAccount() != null ? transaction.getSourceAccount().getId() : null)
                            .amount(transaction.getAmount())
                            .reason("Pending transaction older than 24 hours marked as FAILED")
                            .compensationCreated(compensationCreated)
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    private String generateReference() {
        return "CMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
