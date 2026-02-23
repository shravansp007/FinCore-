package com.bank.app.repository;

import com.bank.app.entity.Account;
import com.bank.app.entity.Account.AccountStatus;
import com.bank.app.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUser(User user);
    List<Account> findByUserId(Long userId);
    Page<Account> findByActiveTrue(Pageable pageable);
    Page<Account> findByActiveTrueAndAccountStatus(AccountStatus accountStatus, Pageable pageable);
    long countByAccountStatus(AccountStatus accountStatus);
    Optional<Account> findByAccountNumber(String accountNumber);
    boolean existsByAccountNumber(String accountNumber);
}
