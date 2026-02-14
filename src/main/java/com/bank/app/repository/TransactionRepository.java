package com.bank.app.repository;

import com.bank.app.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserId(Long userId);

    Page<Transaction> findByUserId(Long userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTransactionDateBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("""
            select distinct t
            from Transaction t
            left join t.sourceAccount sa
            left join t.destinationAccount da
            where sa.id = :accountId
               or da.id = :accountId
            """)
    Page<Transaction> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    Optional<Transaction> findByTransactionReference(String transactionReference);

    long countBySourceAccountIdOrDestinationAccountId(Long sourceAccountId, Long destinationAccountId);

    @Query("""
            select distinct t
            from Transaction t
            left join t.user u
            left join t.sourceAccount sa
            left join sa.user su
            left join t.destinationAccount da
            left join da.user du
            where u.id = :userId
               or su.id = :userId
               or du.id = :userId
            """)
    Page<Transaction> findByUserAccounts(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select distinct t
            from Transaction t
            left join t.user u
            left join t.sourceAccount sa
            left join sa.user su
            left join t.destinationAccount da
            left join da.user du
            where (u.id = :userId
               or su.id = :userId
               or du.id = :userId)
              and t.transactionDate between :start and :end
            """)
    Page<Transaction> findByUserAccountsAndTransactionDateBetween(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );

    @Query("""
            select distinct t
            from Transaction t
            left join t.user u
            left join t.sourceAccount sa
            left join sa.user su
            left join t.destinationAccount da
            left join da.user du
            where u.id = :userId
               or su.id = :userId
               or du.id = :userId
            """)
    List<Transaction> findByUserAccounts(@Param("userId") Long userId);
}
