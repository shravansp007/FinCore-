package com.bank.app.fraud;

import com.bank.app.kafka.model.TransactionEvent;

public interface FraudRule {
    FraudResult evaluate(TransactionEvent transaction, FraudContext context);
}
