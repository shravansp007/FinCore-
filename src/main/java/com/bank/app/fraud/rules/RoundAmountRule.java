package com.bank.app.fraud.rules;

import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.kafka.model.TransactionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(5)
public class RoundAmountRule implements FraudRule {

    private static final String RULE_NAME = "RoundAmountRule";
    private static final int RISK_SCORE = 10;
    private static final BigDecimal ROUND_BASE = new BigDecimal("10000");

    @Override
    public FraudResult evaluate(TransactionEvent transaction, FraudContext context) {
        boolean triggered = transaction.getAmount() != null
                && transaction.getAmount().remainder(ROUND_BASE).compareTo(BigDecimal.ZERO) == 0;

        return FraudResult.builder()
                .flag(triggered)
                .ruleName(RULE_NAME)
                .riskScore(triggered ? RISK_SCORE : 0)
                .reason(triggered
                        ? "Amount is exactly divisible by 10000"
                        : "Amount is not a suspicious round structuring value")
                .build();
    }
}
