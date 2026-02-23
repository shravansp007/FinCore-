package com.bank.app.repository;

import com.bank.app.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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

    @Query("""
            select t
            from Transaction t
            where (t.sourceAccount.id = :accountId or t.destinationAccount.id = :accountId)
              and t.transactionDate between :start and :end
              and t.status = com.bank.app.entity.Transaction$TransactionStatus.COMPLETED
            order by t.transactionDate asc, t.id asc
            """)
    Stream<Transaction> streamByAccountAndTransactionDateBetween(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            select coalesce(sum(
                case
                    when t.destinationAccount.id = :accountId then t.amount
                    when t.sourceAccount.id = :accountId then -t.amount
                    else 0
                end
            ), 0)
            from Transaction t
            where (t.sourceAccount.id = :accountId or t.destinationAccount.id = :accountId)
              and t.transactionDate between :start and :end
              and t.status = com.bank.app.entity.Transaction$TransactionStatus.COMPLETED
            """)
    BigDecimal calculateNetChangeForAccountBetween(
            @Param("accountId") Long accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            select count(t)
            from Transaction t
            where (t.sourceAccount.id = :accountId or t.destinationAccount.id = :accountId)
              and t.transactionDate >= :since
            """)
    long countTransactionsForAccountSince(
            @Param("accountId") Long accountId,
            @Param("since") LocalDateTime since
    );

    Page<Transaction> findByStatusAndTransactionDateBefore(
            Transaction.TransactionStatus status,
            LocalDateTime before,
            Pageable pageable
    );

    Page<Transaction> findByStatus(Transaction.TransactionStatus status, Pageable pageable);
}
