package com.bank.app.fraud;

import com.bank.app.entity.TransferHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudContext {
    private List<TransferHistory> recentTransactionHistory;
    private long velocityCountLast10Minutes;
    private long distinctDestinationCountLast30Minutes;
    private boolean beneficiarySeenBefore;
    private BigDecimal amountThreshold;
}
