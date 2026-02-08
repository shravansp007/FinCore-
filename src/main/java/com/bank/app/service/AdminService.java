package com.bank.app.service;

import com.bank.app.dto.AccountDTO;
import com.bank.app.dto.TransactionResponse;
import com.bank.app.dto.UserSummaryDTO;
import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.User;
import com.bank.app.exception.ResourceNotFoundException;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public List<UserSummaryDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapUserToSummary)
                .collect(Collectors.toList());
    }

    public List<AccountDTO> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::mapAccountToDTO)
                .collect(Collectors.toList());
    }

    public Page<TransactionResponse> getAllTransactions(@NonNull Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(this::mapTransactionToResponse);
    }

    public AccountDTO updateAccountStatus(Long accountId, boolean active) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        account.setActive(active);
        account = accountRepository.save(account);
        log.info("action=admin.account.status_change accountId={} active={}", account.getId(), account.getActive());
        return mapAccountToDTO(account);
    }

    private UserSummaryDTO mapUserToSummary(User user) {
        return UserSummaryDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AccountDTO mapAccountToDTO(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .active(account.getActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private TransactionResponse mapTransactionToResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionReference(t.getTransactionReference())
                .type(t.getType())
                .amount(t.getAmount())
                .description(t.getDescription())
                .sourceAccountNumber(t.getSourceAccount() != null ? t.getSourceAccount().getAccountNumber() : null)
                .destinationAccountNumber(t.getDestinationAccount() != null ? t.getDestinationAccount().getAccountNumber() : null)
                .status(t.getStatus())
                .transactionDate(t.getTransactionDate())
                .build();
    }
}
