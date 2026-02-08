package com.bank.app.repository;

import com.bank.app.entity.Account;
import com.bank.app.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    Optional<Transaction> findByTransactionReference(String transactionReference);
    
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount = :account OR t.destinationAccount = :account ORDER BY t.transactionDate DESC")
    List<Transaction> findByAccount(@Param("account") Account account);
    
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId ORDER BY t.transactionDate DESC")
    Page<Transaction> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);
    
    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN t.sourceAccount sa
            LEFT JOIN t.destinationAccount da
            WHERE (sa.user.id = :userId OR da.user.id = :userId)
            ORDER BY t.transactionDate DESC
            """)
    List<Transaction> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN t.sourceAccount sa
            LEFT JOIN t.destinationAccount da
            WHERE (sa.user.id = :userId OR da.user.id = :userId)
              AND (:startDate IS NULL OR t.transactionDate >= cast(:startDate as timestamp))
              AND (:endDate IS NULL OR t.transactionDate <= cast(:endDate as timestamp))
              AND (
                :direction IS NULL
                OR (
                  :direction = 'CREDIT' AND (
                    (t.type = com.bank.app.entity.Transaction$TransactionType.DEPOSIT AND sa.user.id = :userId)
                    OR (t.type = com.bank.app.entity.Transaction$TransactionType.TRANSFER AND da.user.id = :userId)
                  )
                )
                OR (
                  :direction = 'DEBIT' AND (
                    ((t.type = com.bank.app.entity.Transaction$TransactionType.WITHDRAWAL OR t.type = com.bank.app.entity.Transaction$TransactionType.PAYMENT)
                      AND sa.user.id = :userId)
                    OR (t.type = com.bank.app.entity.Transaction$TransactionType.TRANSFER AND sa.user.id = :userId)
                  )
                )
              )
            """)
    Page<Transaction> findByUserIdWithFilters(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("direction") String direction,
            Pageable pageable
    );
    
    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate AND (t.sourceAccount = :account OR t.destinationAccount = :account)")
    List<Transaction> findByAccountAndDateRange(
            @Param("account") Account account,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN t.sourceAccount sa
            LEFT JOIN t.destinationAccount da
            WHERE (sa.user.id = :userId OR da.user.id = :userId)
              AND (:startDate IS NULL OR t.transactionDate >= cast(:startDate as timestamp))
              AND (:endDate IS NULL OR t.transactionDate <= cast(:endDate as timestamp))
            """)
    List<Transaction> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
