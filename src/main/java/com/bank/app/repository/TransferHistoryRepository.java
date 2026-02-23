package com.bank.app.repository;

import com.bank.app.entity.TransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long> {

    @Query("""
            select count(th)
            from TransferHistory th
            where th.sourceAccountId = :sourceAccountId
              and th.createdAt >= :since
            """)
    long countBySourceAccountSince(@Param("sourceAccountId") Long sourceAccountId, @Param("since") LocalDateTime since);

    boolean existsBySourceAccountIdAndDestinationAccountId(Long sourceAccountId, Long destinationAccountId);

    @Query("""
            select count(distinct th.destinationAccountId)
            from TransferHistory th
            where th.sourceAccountId = :sourceAccountId
              and th.createdAt >= :since
            """)
    long countDistinctDestinationBySourceSince(
            @Param("sourceAccountId") Long sourceAccountId,
            @Param("since") LocalDateTime since
    );

    @Query("""
            select th
            from TransferHistory th
            where th.sourceAccountId = :sourceAccountId
              and th.createdAt >= :since
            order by th.createdAt desc
            """)
    List<TransferHistory> findRecentBySourceAccountSince(
            @Param("sourceAccountId") Long sourceAccountId,
            @Param("since") LocalDateTime since
    );
}
