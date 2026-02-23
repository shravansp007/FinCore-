package com.bank.app.integration;

import com.bank.app.entity.Account;
import com.bank.app.entity.Role;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.User;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fincore_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void repositoryQueries_useRealPostgresAndReturnExpectedRows() {
        User user = userRepository.save(User.builder()
                .firstName("Fin")
                .lastName("Core")
                .email("repo-test@fincore.com")
                .password("encoded")
                .role(Role.USER)
                .enabled(true)
                .build());

        Account source = accountRepository.save(Account.builder()
                .accountNumber("TR-1001")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("10000.00"))
                .currency("INR")
                .user(user)
                .active(true)
                .accountStatus(Account.AccountStatus.ACTIVE)
                .build());

        Account destination = accountRepository.save(Account.builder()
                .accountNumber("TR-1002")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("5000.00"))
                .currency("INR")
                .user(user)
                .active(true)
                .accountStatus(Account.AccountStatus.ACTIVE)
                .build());

        LocalDateTime now = LocalDateTime.now();

        transactionRepository.save(Transaction.builder()
                .transactionReference("TXN-REAL-001")
                .type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("200.00"))
                .description("Transfer test")
                .sourceAccount(source)
                .destinationAccount(destination)
                .status(Transaction.TransactionStatus.COMPLETED)
                .transactionDate(now.minusMinutes(30))
                .user(user)
                .build());

        transactionRepository.save(Transaction.builder()
                .transactionReference("TXN-REAL-002")
                .type(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("100.00"))
                .description("Withdrawal test")
                .sourceAccount(source)
                .destinationAccount(null)
                .status(Transaction.TransactionStatus.COMPLETED)
                .transactionDate(now.minusMinutes(10))
                .user(user)
                .build());

        long count = transactionRepository.countTransactionsForAccountSince(source.getId(), now.minusDays(1));

        assertThat(count).isEqualTo(2L);
        assertThat(transactionRepository.findByTransactionReference("TXN-REAL-001")).isPresent();
        assertThat(transactionRepository.findByUserAccounts(user.getId())).hasSize(2);
    }

    @Test
    void optimisticLockingConflict_throwsWhenSavingStaleAccountVersion() {
        User user = userRepository.save(User.builder()
                .firstName("Opt")
                .lastName("Lock")
                .email("optimistic@fincore.com")
                .password("encoded")
                .role(Role.USER)
                .enabled(true)
                .build());

        Account created = accountRepository.saveAndFlush(Account.builder()
                .accountNumber("LOCK-1001")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .user(user)
                .active(true)
                .accountStatus(Account.AccountStatus.ACTIVE)
                .build());

        Long staleVersion = created.getVersion();

        created.setBalance(new BigDecimal("1100.00"));
        accountRepository.saveAndFlush(created);

        Account staleEntity = Account.builder()
                .id(created.getId())
                .version(staleVersion)
                .accountNumber(created.getAccountNumber())
                .accountType(created.getAccountType())
                .balance(new BigDecimal("900.00"))
                .currency(created.getCurrency())
                .user(user)
                .active(true)
                .accountStatus(Account.AccountStatus.ACTIVE)
                .build();

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> accountRepository.saveAndFlush(staleEntity));
    }
}
