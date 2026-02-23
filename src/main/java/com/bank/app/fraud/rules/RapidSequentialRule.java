package com.bank.app.fraud.rules;

import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.kafka.model.TransactionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(6)
public class RapidSequentialRule implements FraudRule {

    private static final String RULE_NAME = "RapidSequentialRule";
    private static final int RISK_SCORE = 25;

    @Override
    public FraudResult evaluate(TransactionEvent transaction, FraudContext context) {
        boolean triggered = context.getDistinctDestinationCountLast30Minutes() >= 3;
        return FraudResult.builder()
                .flag(triggered)
                .ruleName(RULE_NAME)
                .riskScore(triggered ? RISK_SCORE : 0)
                .reason(triggered
                        ? "Source account transferred to 3+ different destination accounts in 30 minutes"
                        : "Rapid sequential destination pattern not detected")
                .build();
    }
}
