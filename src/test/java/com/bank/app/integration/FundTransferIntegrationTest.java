package com.bank.app.integration;

import com.bank.app.dto.TransactionRequest;
import com.bank.app.entity.Account;
import com.bank.app.entity.Role;
import com.bank.app.entity.Transaction.TransactionType;
import com.bank.app.entity.User;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import com.bank.app.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FundTransferIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void transfer_movesFundsAndCreatesEntries() {
        User user = User.builder()
                .firstName("Pat")
                .lastName("Lee")
                .email("pat@example.com")
                .password("hashed")
                .role(Role.USER)
                .enabled(true)
                .build();
        user = userRepository.save(user);

        Account source = Account.builder()
                .accountNumber("1111111111")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .user(user)
                .active(true)
                .build();
        source = accountRepository.save(source);

        Account destination = Account.builder()
                .accountNumber("2222222222")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("200.00"))
                .currency("INR")
                .user(user)
                .active(true)
                .build();
        destination = accountRepository.save(destination);

        TransactionRequest request = TransactionRequest.builder()
                .type(TransactionType.TRANSFER)
                .amount(new BigDecimal("150.00"))
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .build();

        transactionService.createTransaction(user.getEmail(), request);

        Account updatedSource = accountRepository.findById(source.getId()).orElseThrow();
        Account updatedDestination = accountRepository.findById(destination.getId()).orElseThrow();

        assertThat(updatedSource.getBalance()).isEqualByComparingTo("850.00");
        assertThat(updatedDestination.getBalance()).isEqualByComparingTo("350.00");
        assertThat(transactionRepository.findAll()).hasSize(2);
    }
}
