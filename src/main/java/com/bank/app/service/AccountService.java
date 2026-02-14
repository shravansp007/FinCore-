package com.bank.app.service;

import com.bank.app.dto.AccountDTO;
import com.bank.app.dto.CreateAccountRequest;
import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import com.bank.app.entity.User;
import com.bank.app.exception.ResourceNotFoundException;
import com.bank.app.exception.UnauthorizedException;
import com.bank.app.repository.AccountRepository;
import com.bank.app.repository.TransactionRepository;
import com.bank.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public AccountDTO createAccount(String userEmail, CreateAccountRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        log.info("action=account.create.start userId={} accountType={} currency={}",
                user.getId(), request.getAccountType(), request.getCurrency());

        String accountNumber = generateAccountNumber();
        
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .balance(request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .user(user)
                .active(true)
                .build();

        account = accountRepository.save(account);
        createOpeningBalanceTransactionIfNeeded(user, account);
        log.info("action=account.create.success userId={} accountId={} accountType={} currency={}",
                user.getId(), account.getId(), account.getAccountType(), account.getCurrency());
        return mapToDTO(account);
    }

    public List<AccountDTO> getUserAccounts(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return accountRepository.findByUser(user).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public AccountDTO getAccountById(Long accountId, String userEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("Access denied to this account");
        }

        return mapToDTO(account);
    }

    public Account getAccountEntityById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    public AccountDTO getAccountByNumber(String accountNumber, String userEmail) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getUser().getEmail().equals(userEmail)) {
            throw new UnauthorizedException("Access denied to this account");
        }
        return mapToDTO(account);
    }

    @Transactional
    public void updateBalance(Long accountId, BigDecimal newBalance) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        account.setBalance(newBalance);
        accountRepository.save(account);
    }

    private String generateAccountNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(random.nextInt(10));
        }
        String accountNumber = sb.toString();
        
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            return generateAccountNumber();
        }
        return accountNumber;
    }

    private void createOpeningBalanceTransactionIfNeeded(User user, Account account) {
        BigDecimal openingBalance = account.getBalance();
        if (openingBalance == null || openingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Transaction openingTx = Transaction.builder()
                .transactionReference("OPEN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .type(Transaction.TransactionType.DEPOSIT)
                .amount(openingBalance)
                .description("Opening balance")
                .destinationAccount(account)
                .status(Transaction.TransactionStatus.COMPLETED)
                .transactionDate(account.getCreatedAt() != null ? account.getCreatedAt() : LocalDateTime.now())
                .user(user)
                .build();

        transactionRepository.save(openingTx);
    }

    private AccountDTO mapToDTO(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .maskedAccountNumber(maskAccountNumber(account.getAccountNumber()))
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .active(account.getActive())
                .createdAt(account.getCreatedAt())
                .build();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        String last4 = accountNumber.substring(accountNumber.length() - 4);
        return "****" + last4;
    }
}
