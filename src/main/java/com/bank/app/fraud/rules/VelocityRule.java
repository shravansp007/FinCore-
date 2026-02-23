package com.bank.app.fraud.rules;

import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.kafka.model.TransactionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class VelocityRule implements FraudRule {

    private static final String RULE_NAME = "VelocityRule";
    private static final int RISK_SCORE = 30;

    @Override
    public FraudResult evaluate(TransactionEvent transaction, FraudContext context) {
        boolean triggered = context.getVelocityCountLast10Minutes() > 5;
        return FraudResult.builder()
                .flag(triggered)
                .ruleName(RULE_NAME)
                .riskScore(triggered ? RISK_SCORE : 0)
                .reason(triggered
                        ? "More than 5 transactions from same account in last 10 minutes"
                        : "Velocity check passed")
                .build();
    }
}
