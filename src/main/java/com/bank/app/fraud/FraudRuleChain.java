package com.bank.app.fraud;

import com.bank.app.kafka.model.TransactionEvent;

import java.util.ArrayList;
import java.util.List;

public class FraudRuleChain {

    private final List<FraudRule> rules;

    public FraudRuleChain(List<FraudRule> rules) {
        this.rules = rules;
    }

    public List<FraudResult> evaluateAll(TransactionEvent transaction, FraudContext context) {
        List<FraudResult> results = new ArrayList<>();
        for (FraudRule rule : rules) {
            results.add(rule.evaluate(transaction, context));
        }
        return results;
    }
}
