package com.bank.app.fraud.rules;

import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.kafka.model.TransactionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(2)
public class AmountThresholdRule implements FraudRule {

    private static final String RULE_NAME = "AmountThresholdRule";
    private static final int RISK_SCORE = 25;

    @Override
    public FraudResult evaluate(TransactionEvent transaction, FraudContext context) {
        BigDecimal threshold = context.getAmountThreshold();
        boolean triggered = transaction.getAmount() != null
                && threshold != null
                && transaction.getAmount().compareTo(threshold) > 0;

        return FraudResult.builder()
                .flag(triggered)
                .ruleName(RULE_NAME)
                .riskScore(triggered ? RISK_SCORE : 0)
                .reason(triggered
                        ? "Transaction amount exceeds configured threshold " + threshold
                        : "Amount threshold check passed")
                .build();
    }
}
