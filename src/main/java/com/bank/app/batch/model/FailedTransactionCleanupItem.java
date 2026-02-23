package com.bank.app.batch.model;

import com.bank.app.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedTransactionCleanupItem {
    private Transaction transaction;
    private boolean compensationRequired;
}
