package com.bank.app.service;

import com.bank.app.dto.CreateAccountRequest;
import com.bank.app.entity.Account;
import com.bank.app.entity.User;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccount_setsDefaultsAndPersists() {
        User user = User.builder()
                .id(10L)
                .firstName("Sam")
                .lastName("Lee")
                .email("sam@example.com")
                .password("hashed")
                .build();

        CreateAccountRequest request = CreateAccountRequest.builder()
                .accountType(Account.AccountType.SAVINGS)
                .initialDeposit(new BigDecimal("2500.00"))
                .currency("INR")
                .build();

        when(userRepository.findByEmail("sam@example.com")).thenReturn(Optional.of(user));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account acc = invocation.getArgument(0);
            acc.setId(101L);
            return acc;
        });

        var dto = accountService.createAccount("sam@example.com", request);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account saved = accountCaptor.getValue();

        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getAccountType()).isEqualTo(Account.AccountType.SAVINGS);
        assertThat(saved.getBalance()).isEqualByComparingTo("2500.00");
        assertThat(saved.getCurrency()).isEqualTo("INR");
        assertThat(dto.getId()).isEqualTo(101L);
        assertThat(dto.getBalance()).isEqualByComparingTo("2500.00");
    }
}
