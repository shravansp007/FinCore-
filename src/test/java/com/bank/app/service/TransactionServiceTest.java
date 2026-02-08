package com.bank.app.service;

import com.bank.app.dto.AccountOperationRequest;
import com.bank.app.entity.Account;
import com.bank.app.entity.Role;
import com.bank.app.entity.User;
import com.bank.app.exception.BadRequestException;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BeneficiaryService beneficiaryService;

    @Mock
    private NotificationService notificationService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                accountRepository,
                userRepository,
                beneficiaryService,
                notificationService,
                new BigDecimal("1.00"),
                new BigDecimal("100000.00")
        );
    }

    @Test
    void deposit_increasesBalance() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .role(Role.USER)
                .enabled(true)
                .build();

        Account account = Account.builder()
                .id(100L)
                .accountNumber("1234567890")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .user(user)
                .active(true)
                .build();

        AccountOperationRequest request = AccountOperationRequest.builder()
                .accountId(100L)
                .amount(new BigDecimal("250.00"))
                .description("Test deposit")
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.deposit("user@example.com", request);

        assertThat(account.getBalance()).isEqualByComparingTo("1250.00");
    }

    @Test
    void withdraw_rejectsInsufficientBalance() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .role(Role.USER)
                .enabled(true)
                .build();

        Account account = Account.builder()
                .id(100L)
                .accountNumber("1234567890")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("100.00"))
                .user(user)
                .active(true)
                .build();

        AccountOperationRequest request = AccountOperationRequest.builder()
                .accountId(100L)
                .amount(new BigDecimal("250.00"))
                .description("Test withdraw")
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.findById(100L)).thenReturn(Optional.of(account));

        assertThrows(BadRequestException.class,
                () -> transactionService.withdraw("user@example.com", request));
    }
}
