package com.bank.app.batch.writer;

import com.bank.app.entity.Account;
import com.bank.app.kafka.AccountEventPublisher;
import com.bank.app.kafka.model.DormantAccountEvent;
import com.bank.app.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DormantAccountItemWriter implements ItemWriter<Account> {

    private final AccountRepository accountRepository;
    private final AccountEventPublisher accountEventPublisher;

    @Override
    public void write(Chunk<? extends Account> chunk) {
        for (Account account : chunk.getItems()) {
            account.setAccountStatus(Account.AccountStatus.DORMANT);
            account.setActive(false);
            accountRepository.save(account);

            accountEventPublisher.publishDormantAccount(
                    DormantAccountEvent.builder()
                            .accountId(account.getId())
                            .accountNumber(account.getAccountNumber())
                            .accountHolderEmail(account.getUser() != null ? account.getUser().getEmail() : null)
                            .status(Account.AccountStatus.DORMANT.name())
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }
}
