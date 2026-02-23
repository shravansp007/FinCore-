package com.bank.app.fraud.rules;

import com.bank.app.fraud.FraudContext;
import com.bank.app.fraud.FraudResult;
import com.bank.app.fraud.FraudRule;
import com.bank.app.kafka.model.TransactionEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class NewBeneficiaryRule implements FraudRule {

    private static final String RULE_NAME = "NewBeneficiaryRule";
    private static final int RISK_SCORE = 20;

    @Override
    public FraudResult evaluate(TransactionEvent transaction, FraudContext context) {
        boolean triggered = !context.isBeneficiarySeenBefore();
        return FraudResult.builder()
                .flag(triggered)
                .ruleName(RULE_NAME)
                .riskScore(triggered ? RISK_SCORE : 0)
                .reason(triggered
                        ? "First-ever transfer to destination account from this source account"
                        : "Beneficiary has prior transfer history")
                .build();
    }
}
